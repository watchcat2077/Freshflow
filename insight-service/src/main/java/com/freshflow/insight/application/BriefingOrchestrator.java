package com.freshflow.insight.application;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Application service (spec-01 &sect;2.1) &mdash; FR15's use case: the store owner asks for the
 * day's briefing, so ask the agent to write one, ground its numbers in the service's own records,
 * persist what it did, and hand the briefing back.
 *
 * <p>Everything that must be right regardless of which model is behind {@link BriefingAgent} lives
 * here, and nothing else does:
 *
 * <ol>
 *   <li><b>The business date.</b> The caller may name one; otherwise it is today in UTC, from the
 *       injected {@link Clock} (spec-04 {@code BriefingRequest}). The agent is told which day it
 *       is briefing on and is not asked to work it out.</li>
 *   <li><b>The counts.</b> {@code riskCount} and {@code recommendationCount} are computed here,
 *       after the agent returns, from the same services its tools read &mdash;
 *       {@link RiskQueryService} and {@link AgentTraceStore} &mdash; and never from the model's
 *       text. spec-02 FR15 requires it in terms: "so no number in the response depends on the
 *       model". The summary may say ninety-nine batches are at risk; the field says how many are.
 *       A recommendation is counted when FR14's trace records a published outcome, which spec-06
 *       &sect;6 defines as a discount tier followed by its window; {@code NO_MARKDOWN} and
 *       {@code PROPOSED_BUT_UNRESOLVABLE} are decisions the agent made that day, and the tool
 *       lists them, but nothing was recommended.</li>
 *   <li><b>The narrative.</b> A blank summary is replaced by {@link #NO_NARRATIVE}, a fixed
 *       sentence saying so. Nothing a model writes, or fails to write, makes this method throw:
 *       the counts stand on their own and the tool calls are persisted either way, so the briefing
 *       is auditable even when it is not readable.</li>
 *   <li><b>The record.</b> The briefing and its ordered tool calls are written through
 *       {@link BriefingStore} under an identifier of the form {@code BRF-} plus eight upper-case
 *       hexadecimal characters, and served back by {@code GET /api/insight/briefings/&#123;id&#125;}.
 *       A POST is not idempotent and is not meant to be: each request is a new briefing, generated
 *       from the records as they stand at that moment.</li>
 * </ol>
 *
 * <p><b>What is not here, and must never be.</b> No proposal, no price, no call to the Pricing
 * context (team ruling R5, spec-06 &sect;7). The agent behind this orchestrator reads three views
 * this service already serves and writes prose about them. FR3's write-offs are not built, so no
 * waste figure is computed here either; spec-02 FR15 says the briefing "states that explicitly
 * rather than inventing a number", and the prompt in {@code LangChain4jBriefingAgent} does.
 *
 * <p><b>A failure of the model itself is not swallowed.</b> If the agent cannot reach its model,
 * the exception propagates and no briefing is written, exactly as FR14's orchestrator behaves:
 * recording "the model was unreachable" as a briefing would put an outage into the audit record
 * permanently.
 *
 * <p>Plain constructor arguments and no Spring annotation, as {@link MarkdownAgentOrchestrator}
 * has, so that every orchestration test builds one in a line with no context and no model. Its
 * bean is declared in {@code infrastructure}'s {@code BriefingAgentConfiguration}, under the same
 * profile and credential guards as FR14's.
 */
public class BriefingOrchestrator {

    /** spec-04 illustrates no format for {@code briefingId}; this is ours, chosen to be greppable. */
    static final String BRIEFING_ID_PREFIX = "BRF-";

    /**
     * Stored as the summary when the agent wrote nothing. A sentence rather than an empty string,
     * so that a reader of the stored briefing learns what happened instead of wondering whether a
     * column was lost.
     */
    static final String NO_NARRATIVE = "The briefing agent produced no narrative for this business "
            + "date. The risk and recommendation counts were computed by the service from its own "
            + "records and stand on their own.";

    private final BriefingAgent agent;
    private final BriefingToolRegistry tools;
    private final BriefingStore briefingStore;
    private final RiskQueryService riskQueryService;
    private final AgentTraceStore agentTraceStore;
    private final Clock clock;

    public BriefingOrchestrator(BriefingAgent agent,
                                BriefingToolRegistry tools,
                                BriefingStore briefingStore,
                                RiskQueryService riskQueryService,
                                AgentTraceStore agentTraceStore,
                                Clock clock) {
        this.agent = agent;
        this.tools = tools;
        this.briefingStore = briefingStore;
        this.riskQueryService = riskQueryService;
        this.agentTraceStore = agentTraceStore;
        this.clock = clock;
    }

    /**
     * FR15's use case, from request to persisted briefing.
     *
     * @param businessDate the day to brief on, or {@code null} for today in UTC
     * @return the briefing, already persisted
     */
    public EndOfDayBriefing brief(LocalDate businessDate) {
        throw new UnsupportedOperationException("FR15");
    }
}
