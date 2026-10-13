package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import com.freshflow.insight.application.AgentUnavailableException;
import com.freshflow.insight.application.AgentDeliberation;
import com.freshflow.insight.application.AgentToolRegistry;
import com.freshflow.insight.application.MarkdownDecisionAgent;
import com.freshflow.insight.application.MarkdownProposal;
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
import java.util.Arrays;
import java.util.List;

/**
 * The runtime adapter behind {@link MarkdownDecisionAgent} &mdash; a ReAct tool-use loop over a
 * LangChain4j {@link ChatModel} (FR14, ADR 0004, spec-06 &sect;8).
 *
 * <p><b>The model library reaches exactly two classes</b> &mdash; this one and
 * {@link MarkdownAgentConfiguration}, which builds the client and hands it in. No type under
 * {@code application} imports {@code dev.langchain4j}, which is ADR 0004's first verification
 * check, and no test source does either. Everything FR14 must be trusted to do correctly
 * &mdash; the trace, the idempotency, the single publication, the promotion window &mdash; is on
 * the other side of the port, in {@code MarkdownAgentOrchestrator}, where it is asserted by tests
 * that need neither a key, a network nor a broker.
 *
 * <h2>The loop</h2>
 * <ol>
 *   <li>A system message stating the task and a user message stating the risk.</li>
 *   <li>Ask the model, offering the three tool specifications and nothing else (R4).</li>
 *   <li>For every tool call it makes: translate the arguments through
 *       {@link MarkdownToolArguments}, run the tool through the real {@link AgentToolRegistry},
 *       record a {@link ToolCall} numbered from 1, and hand the result back to the model.</li>
 *   <li>Stop when the model answers without calling a tool, or when {@code maxSteps} round trips
 *       have been made.</li>
 * </ol>
 *
 * <p><b>The tool-argument encoding is a contract and not an implementation detail.</b> The registry
 * reads a flat string; the model emits JSON. {@link MarkdownToolArguments} is the translation, and
 * {@code MarkdownToolArgumentsTest} pins it, because the encoding is load-bearing.
 *
 * <p><b>Tool failures are answers, not exceptions.</b> {@code AgentToolRegistry.invoke} never
 * throws; a mis-called tool comes back as {@code error=...} text which is fed to the model like
 * any other result, so the model may correct itself. That is the whole mechanism of the pattern,
 * and the trace records the failed call as faithfully as a successful one.
 *
 * <p><b>A failure of the model itself is NOT swallowed.</b> If the call cannot be made &mdash; no
 * network, a rejected credential, a timeout &mdash; the exception propagates out of
 * {@code deliberate}, the orchestrator writes no trace, and the at-least-once delivery of the
 * trigger is free to try again. Recording "the model was unreachable" as a decision would put a
 * transient outage into the audit record permanently, and spec-06 &sect;5's idempotency check
 * would then suppress every retry.
 *
 * <h2>What the prompt may not contain</h2>
 * <p>Team ruling R5 and spec-06 &sect;7: this service holds no downstream invariant of the Pricing
 * context, "not a constant, not a check, not a sentence in a prompt template". The instructions
 * below therefore tell the model what a markdown proposal is and say nothing whatever about what
 * makes one acceptable downstream. <b>The agent must be able to propose something Pricing will
 * reject</b> &mdash; spec-01 &sect;1.5 makes that context an Anti-Corruption Layer against this
 * producer, and a rejection is that layer being seen to work rather than a defect in this prompt.
 * {@code AntiCorruptionLayerGuardTest} scans string literals, which is what makes the rule
 * enforceable here at all.
 */
public class LangChain4jMarkdownDecisionAgent implements MarkdownDecisionAgent {

    private static final Logger log =
            LoggerFactory.getLogger(LangChain4jMarkdownDecisionAgent.class);

    /** The prefix {@code proposeMarkdown} answers with when it has accepted a proposal. */
    private static final String ACCEPTED = "recorded ";

    /**
     * What the model is asked to do.
     *
     * <p>It describes the situation, the tools and the shape of an answer. It states no threshold
     * and no arithmetic the model must satisfy, because no such rule belongs to this bounded
     * context (R5). The judgement is the model's; whether the result is honoured is decided
     * downstream and independently.
     */
    static final String SYSTEM_PROMPT = """
            You decide whether a batch of perishable stock should be marked down.

            A batch has been flagged because it is not selling fast enough to clear before it
            expires. Your job is to gather the evidence available to you and then either propose a
            markdown or decide that none is warranted. Both are complete answers.

            Work in this order:
            1. Call getBatchDetails for the flagged batch to see what is left, when it expires and
               what it cost.
            2. Call getSellThroughRate for its SKU to see how fast it is currently selling. If the
               rate is reported as unknown, that means no measurement is available, NOT that
               nothing is selling.
            3. Then decide.

            To propose, call proposeMarkdown exactly once with:
              batchId        the flagged batch, and no other
              tier           one of PERCENT_20, PERCENT_30, PERCENT_50, PERCENT_60
              effectiveFrom  an ISO-8601 instant, normally now
              rationale      one or two sentences citing the numbers you actually saw

            Do not propose more than one markdown. Do not set or calculate a price: you recommend a
            discount tier and a start time, and nothing else. You do not choose when the markdown
            ends; that is derived from the batch's expiry.

            If a tool answers with text beginning error= then read it, correct your call and try
            again.

            If you decide against a markdown, do not call proposeMarkdown. Reply with one or two
            sentences saying why, citing the numbers you saw.
            """;

    private final ChatModel chatModel;
    private final Clock clock;
    private final int maxSteps;

    public LangChain4jMarkdownDecisionAgent(ChatModel chatModel, Clock clock, int maxSteps) {
        this.chatModel = chatModel;
        this.clock = clock;
        this.maxSteps = Math.max(1, maxSteps);
    }

    @Override
    public AgentDeliberation deliberate(SellThroughRiskDetectedEvent trigger,
                                        AgentToolRegistry tools) {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(SystemMessage.from(SYSTEM_PROMPT));
        conversation.add(UserMessage.from(describe(trigger)));

        List<ToolSpecification> specifications = toolSpecifications(tools);
        List<ToolCall> calls = new ArrayList<>();
        MarkdownProposal proposal = null;
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

            for (ToolExecutionRequest request : answer.toolExecutionRequests()) {
                String arguments = MarkdownToolArguments.encode(request.name(), request.arguments());
                String result = tools.invoke(request.name(), arguments);

                calls.add(new ToolCall(calls.size() + 1, request.name(), arguments, result,
                        clock.instant()));
                conversation.add(ToolExecutionResultMessage.from(request, result));

                // The proposal is taken only from a call the registry ACCEPTED. A rejected call is
                // still recorded above -- what the agent tried is part of the audit record -- but
                // it proposed nothing, and spec-06 s4.3 is what decided that.
                if (AgentToolRegistry.PROPOSE_MARKDOWN.equals(request.name())
                        && result.startsWith(ACCEPTED)) {
                    MarkdownProposal decoded =
                            MarkdownToolArguments.decodeProposal(request.arguments());
                    if (decoded != null) {
                        proposal = decoded;
                    }
                }
            }
        }

        String rationale = rationale(proposal, closing, calls.size());
        log.info("[fr14-deliberation] batchId={} toolCalls={} proposed={}",
                trigger.batchId(), calls.size(), proposal != null);

        return new AgentDeliberation(List.copyOf(calls), proposal, rationale);
    }

    // --------------------------------------------------------------------------------- the tools

    /**
     * The three tools, declared to the model exactly as {@link AgentToolRegistry#toolNames()}
     * offers them.
     *
     * <p>Built from the registry's own names rather than from literals here, so that a tool added
     * or removed there cannot silently disagree with what the model is told it may call. R4 caps
     * the set at three; the assertion that it is three lives in {@code AgentToolRegistryTest}.
     *
     * <p>The property names are {@link MarkdownToolArguments}' constants, because the schema the
     * model fills in and the encoder that reads what it filled in have to name the same fields.
     */
    private static List<ToolSpecification> toolSpecifications(AgentToolRegistry tools) {
        List<ToolSpecification> specifications = new ArrayList<>();

        if (tools.toolNames().contains(AgentToolRegistry.GET_BATCH_DETAILS)) {
            specifications.add(ToolSpecification.builder()
                    .name(AgentToolRegistry.GET_BATCH_DETAILS)
                    .description("Remaining quantity, expiry timestamp and unit cost for one batch.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty(MarkdownToolArguments.BATCH_ID,
                                    "The batch identifier, for example BAT-0001.")
                            .required(MarkdownToolArguments.BATCH_ID)
                            .build())
                    .build());
        }

        if (tools.toolNames().contains(AgentToolRegistry.GET_SELL_THROUGH_RATE)) {
            specifications.add(ToolSpecification.builder()
                    .name(AgentToolRegistry.GET_SELL_THROUGH_RATE)
                    .description("The current sell-through rate for one SKU, in units per hour, "
                            + "and its ratio to the stored baseline where one exists.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty(MarkdownToolArguments.SKU,
                                    "The stock-keeping unit, for example SKU-YOG-1KG.")
                            .required(MarkdownToolArguments.SKU)
                            .build())
                    .build());
        }

        if (tools.toolNames().contains(AgentToolRegistry.PROPOSE_MARKDOWN)) {
            specifications.add(ToolSpecification.builder()
                    .name(AgentToolRegistry.PROPOSE_MARKDOWN)
                    .description("Record a markdown recommendation for the flagged batch. "
                            + "Call this at most once, and only for the batch you were given.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty(MarkdownToolArguments.BATCH_ID,
                                    "The flagged batch, and no other.")
                            .addEnumProperty(MarkdownToolArguments.TIER, tierNames(),
                                    "The discount tier to recommend.")
                            .addStringProperty(MarkdownToolArguments.EFFECTIVE_FROM,
                                    "When the markdown should start, as an ISO-8601 instant.")
                            .addStringProperty(MarkdownToolArguments.RATIONALE,
                                    "One or two sentences citing the numbers you saw.")
                            .required(MarkdownToolArguments.BATCH_ID, MarkdownToolArguments.TIER,
                                    MarkdownToolArguments.EFFECTIVE_FROM,
                                    MarkdownToolArguments.RATIONALE)
                            .build())
                    .build());
        }

        return specifications;
    }

    private static List<String> tierNames() {
        return Arrays.stream(DiscountTier.values()).map(Enum::name).toList();
    }

    // --------------------------------------------------------------------------------- helpers

    /** The risk, in the terms {@code sell-through-risk-detected} carries it. */
    private static String describe(SellThroughRiskDetectedEvent trigger) {
        return "A batch has been flagged as at risk of not clearing before it expires.\n"
                + "batchId: " + trigger.batchId() + "\n"
                + "sku: " + trigger.sku() + "\n"
                + "remainingQuantity: " + trigger.remainingQuantity() + "\n"
                + "hoursToExpiry: " + trigger.hoursToExpiry() + "\n"
                + "currentVelocityPerHour: " + trigger.currentVelocity() + "\n"
                + "projectedShortfall: " + trigger.projectedShortfall() + " units\n"
                + "detectedAt: " + trigger.occurredAt() + "\n\n"
                + "Decide what to do about it.";
    }

    /**
     * FR14's written justification, which is never absent.
     *
     * <p>A proposal carries its own rationale, and it is preferred because it is the one the
     * recommendation is published with; without a proposal the model's closing text is the
     * explanation for having declined. Only if both are missing does this supply a sentence of its
     * own, and that sentence says what actually happened rather than inventing a judgement, because
     * spec-06 &sect;6 puts the rationale on the trace in both outcomes and a blank one would make
     * the audit record useless at exactly the moment it is most needed.
     */
    private static String rationale(MarkdownProposal proposal, String closing, int toolCalls) {
        if (proposal != null && proposal.rationale() != null && !proposal.rationale().isBlank()) {
            return proposal.rationale();
        }
        if (closing != null && !closing.isBlank()) {
            return closing.trim();
        }
        return "The agent stopped after " + toolCalls + " tool call(s) without stating a "
                + "conclusion. No markdown was proposed.";
    }

    // ------------------------------------------------------------------ the port boundary

    /** Names this adapter in the exception the application layer sees when the model is down. */
    static final String AGENT_LABEL = "FR14 markdown agent";

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
     * Added 5 October 2026 after a live run spent the model's daily free quota and the raw
     * 429 reached a controller and a Kafka consumer.
     */
    private ChatResponse ask(ChatRequest request) {
        try {
            return chatModel.chat(request);
        } catch (RuntimeException providerFailure) {
            throw new AgentUnavailableException(AGENT_LABEL, providerFailure);
        }
    }
}
