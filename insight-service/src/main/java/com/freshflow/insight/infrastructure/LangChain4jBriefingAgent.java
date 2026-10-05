package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentUnavailableException;
import com.freshflow.insight.application.BriefingAgent;
import com.freshflow.insight.application.BriefingDeliberation;
import com.freshflow.insight.application.BriefingRequest;
import com.freshflow.insight.application.BriefingToolRegistry;
import com.freshflow.insight.application.ToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The runtime adapter behind {@link BriefingAgent} &mdash; a bounded tool-use loop over a
 * LangChain4j {@link ChatModel} (FR15, ADR 0004, spec-06 &sect;11).
 *
 * <p>The same shape as {@link LangChain4jMarkdownDecisionAgent}, and deliberately a separate class
 * rather than a shared loop with two configurations: FR14's loop reads a proposal back out of an
 * accepted tool call, FR15's reads a closing narrative, and a loop abstract enough to do both would
 * be harder to review than two loops that each do one thing. The model bean is shared; the loops
 * are not. The model library reaches this class and {@link BriefingAgentConfiguration} and no type
 * under {@code application} (ADR 0004's first verification check).
 *
 * <h2>The loop</h2>
 * <ol>
 *   <li>A system message stating the task and a user message stating the business date.</li>
 *   <li>Ask the model, offering the three read-only tool specifications and nothing else.</li>
 *   <li>For every tool call it makes: translate the arguments through
 *       {@link BriefingToolArguments}, run the tool through the real
 *       {@link BriefingToolRegistry}, record a {@link ToolCall} numbered from 1, and hand the
 *       result back.</li>
 *   <li>Stop when the model answers without calling a tool &mdash; that answer is the briefing
 *       &mdash; or when {@code maxSteps} round trips have been made.</li>
 * </ol>
 *
 * <p><b>A missing narrative is returned as blank, not invented here.</b> If the loop ends at
 * {@code maxSteps} without a closing answer, the deliberation carries the tool calls and an empty
 * summary, and {@code BriefingOrchestrator} stores its fixed sentence in place of a narrative.
 * FR14's adapter composes a fallback rationale of its own because spec-06 &sect;6 puts the
 * rationale on the trace in every outcome; FR15 puts that decision in the orchestrator, where it
 * is tested.
 *
 * <p><b>Tool failures are answers, not exceptions; a failure of the model itself is not
 * swallowed.</b> Both as for FR14: {@code error=…} text is fed back to the model so it may correct
 * itself, and an unreachable model propagates out of {@code brief} so that no briefing records an
 * outage as a summary.
 *
 * <h2>What the prompt may not contain</h2>
 * <p>Team ruling R5 and spec-06 &sect;7 apply to this agent exactly as to FR14's. The prompt
 * below describes what the briefing is for and what the tools answer. It names no threshold, no
 * price and nothing about what makes a markdown acceptable downstream, and
 * {@code AntiCorruptionLayerGuardTest} scans it as a string literal to make sure it stays that
 * way. It also tells the model, in terms, that write-off totals are not available: spec-02 FR15
 * says waste figures depend on FR3, which is not built, and that the briefing "states that
 * explicitly rather than inventing a number".
 */
public class LangChain4jBriefingAgent implements BriefingAgent {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jBriefingAgent.class);

    /**
     * What the model is asked to do.
     *
     * <p>It states the audience, the order in which to gather evidence, the shape of the answer
     * and the one fact the briefing must state rather than guess. It recommends nothing and asks
     * the model to recommend nothing: the agent's tools cannot write, and the prompt says so too.
     */
    static final String SYSTEM_PROMPT = """
            You write the end-of-day briefing for the owner of a small grocery store. The store
            sells perishable stock in batches. During the day an automated markdown agent examines
            batches that are not selling fast enough to clear before they expire and records, for
            each one, whether it recommended a markdown and why. Your briefing summarises that day
            for the owner.

            Gather the evidence first, then write. Work in this order:
            1. Call listCurrentRisks to see which batches are still at risk of expiring unsold
               right now.
            2. Call listRecommendations with the business date you were given to see which
               batches the markdown agent examined that day, what it recommended for each, and
               why. A line whose finalRecommendation is NO_MARKDOWN was examined and left alone;
               one that reads PROPOSED_BUT_UNRESOLVABLE could not be acted on.
            3. Optionally call getSellThroughRate for any SKU you want to say something specific
               about. If the rate is reported as unknown, that means no measurement is
               available, NOT that nothing is selling.
            4. Then write the briefing.

            The briefing is four to eight sentences of plain language, with no headings, no
            bullet points and no markdown formatting. Cover, in this order: what was recommended
            for markdown today and why; what is selling through; and what is still at risk and
            may need attention tomorrow. Name the batches and cite the numbers you actually saw,
            and never invent a figure the tools did not give you. If a tool answers none, there
            is nothing in that category, and say so plainly.

            State plainly that write-off totals are not available in this briefing, because
            write-offs are not recorded by the system yet.

            If a tool answers with text beginning error= then read it, correct your call and try
            again.

            You recommend nothing and change nothing. You are summarising decisions that have
            already been made and recorded.
            """;

    private final ChatModel chatModel;
    private final Clock clock;
    private final int maxSteps;

    public LangChain4jBriefingAgent(ChatModel chatModel, Clock clock, int maxSteps) {
        this.chatModel = chatModel;
        this.clock = clock;
        this.maxSteps = Math.max(1, maxSteps);
    }

    @Override
    public BriefingDeliberation brief(BriefingRequest request, BriefingToolRegistry tools) {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(SystemMessage.from(SYSTEM_PROMPT));
        conversation.add(UserMessage.from(describe(request)));

        List<ToolSpecification> specifications = toolSpecifications(tools);
        List<ToolCall> calls = new ArrayList<>();
        String closing = null;

        for (int step = 1; step <= maxSteps; step++) {
            ChatResponse response = ask(ChatRequest.builder()
                    .messages(conversation)
                    .toolSpecifications(specifications)
                    .build());

            AiMessage answer = response.aiMessage();
            conversation.add(answer);

            if (!answer.hasToolExecutionRequests()) {
                closing = answer.text();
                break;
            }

            for (ToolExecutionRequest request1 : answer.toolExecutionRequests()) {
                String arguments = BriefingToolArguments.encode(request1.name(), request1.arguments());
                String result = tools.invoke(request1.name(), arguments);

                calls.add(new ToolCall(calls.size() + 1, request1.name(), arguments, result,
                        clock.instant()));
                conversation.add(ToolExecutionResultMessage.from(request1, result));
            }
        }

        String summary = closing == null ? "" : closing.trim();
        log.info("[fr15-briefing] businessDate={} toolCalls={} narrated={}",
                request.businessDate(), calls.size(), !summary.isEmpty());

        return new BriefingDeliberation(List.copyOf(calls), summary);
    }

    // --------------------------------------------------------------------------------- the tools

    /**
     * The three tools, declared to the model exactly as {@link BriefingToolRegistry#toolNames()}
     * offers them, and built from the registry's own names so that the two cannot disagree. The
     * property names are {@link BriefingToolArguments}' constants, because the schema the model
     * fills in and the encoder that reads it have to name the same fields.
     */
    private static List<ToolSpecification> toolSpecifications(BriefingToolRegistry tools) {
        List<ToolSpecification> specifications = new ArrayList<>();

        if (tools.toolNames().contains(BriefingToolRegistry.LIST_CURRENT_RISKS)) {
            specifications.add(ToolSpecification.builder()
                    .name(BriefingToolRegistry.LIST_CURRENT_RISKS)
                    .description("The batches currently flagged as at risk of expiring unsold, "
                            + "worst first, one per line. Takes no arguments.")
                    .build());
        }

        if (tools.toolNames().contains(BriefingToolRegistry.LIST_RECOMMENDATIONS)) {
            specifications.add(ToolSpecification.builder()
                    .name(BriefingToolRegistry.LIST_RECOMMENDATIONS)
                    .description("The markdown decisions the automated agent recorded on one "
                            + "business date, one per line: the batch, the outcome and the "
                            + "reasoning.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty(BriefingToolArguments.BUSINESS_DATE,
                                    "The business date, as an ISO-8601 date such as 2026-10-04.")
                            .required(BriefingToolArguments.BUSINESS_DATE)
                            .build())
                    .build());
        }

        if (tools.toolNames().contains(BriefingToolRegistry.GET_SELL_THROUGH_RATE)) {
            specifications.add(ToolSpecification.builder()
                    .name(BriefingToolRegistry.GET_SELL_THROUGH_RATE)
                    .description("The current sell-through rate for one SKU, in units per hour, "
                            + "and its ratio to the stored baseline where one exists.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty(BriefingToolArguments.SKU,
                                    "The stock-keeping unit, for example SKU-YOG-1KG.")
                            .required(BriefingToolArguments.SKU)
                            .build())
                    .build());
        }

        return specifications;
    }

    // --------------------------------------------------------------------------------- helpers

    /** The request, in the terms the orchestrator fixed: the day to brief on, and the time now. */
    private static String describe(BriefingRequest request) {
        return "Write the end-of-day briefing for business date " + request.businessDate() + ".\n"
                + "The time now is " + request.now() + ".\n\n"
                + "Gather the evidence with your tools first, then write the briefing.";
    }

    // ------------------------------------------------------------------ the port boundary

    /** Names this adapter in the exception the application layer sees when the model is down. */
    static final String AGENT_LABEL = "FR15 briefing agent";

    /**
     * The one call that leaves this service for the provider, and the one place a provider
     * exception is translated.
     *
     * <p>LangChain4j signals a 429, a timeout, a bad credential or a 5xx with its own exception
     * types, all under {@code RuntimeException}. None of them may cross the port: the
     * application layer knows {@link AgentUnavailableException} and nothing of the provider
     * (ADR 0004). The cause is kept, so a debug log can still show the provider's full answer,
     * and the reason the exception carries is one bounded, redacted line.
     *
     * <p>Only the model call is wrapped. A tool that fails answers {@code error=...} text to the
     * model, as spec-06 &sect;4 fixes; a bug in this loop is a bug and stays one.
     * Added 5 October 2026 after the live run of that afternoon spent the model's daily free
     * quota and the raw 429 reached a controller and a Kafka consumer.
     */
    private ChatResponse ask(ChatRequest request) {
        try {
            return chatModel.chat(request);
        } catch (RuntimeException providerFailure) {
            throw new AgentUnavailableException(AGENT_LABEL, providerFailure);
        }
    }
}
