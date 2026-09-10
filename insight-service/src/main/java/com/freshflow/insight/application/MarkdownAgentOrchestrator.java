package com.freshflow.insight.application;

import com.freshflow.contracts.MarkdownRecommendedEvent;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service (spec-01 &sect;2.1) &mdash; FR14's use case: a batch has been flagged at risk,
 * so ask the agent what to do about it, record what it did, and carry its conclusion to Pricing.
 *
 * <p>Everything that must be right regardless of which model is behind
 * {@link MarkdownDecisionAgent} lives here, and nothing else does:
 *
 * <ol>
 *   <li><b>Idempotency.</b> The trigger's {@code eventId} is looked up through
 *       {@link AgentTraceStore#findByTriggerEventId(String)} before anything happens. A redelivery
 *       returns the stored trace and does nothing else &mdash; no second deliberation, no second
 *       trace, no second publication. spec-01 &sect;2.6 requires consumers to deduplicate on
 *       {@code eventId}; this is FR14 doing so.</li>
 *   <li><b>The trace.</b> Written once the deliberation returns, whether or not it proposed
 *       anything. FR14 makes the trace the audit record of the decision, and a decision to leave a
 *       batch alone is a decision. A trace is never a by-product of success.</li>
 *   <li><b>The publication.</b> At most one {@code markdown-recommended} per trace, published here
 *       rather than by the {@code proposeMarkdown} tool. How many times a language model chooses to
 *       call a tool must not decide how many events cross into another bounded context, and the
 *       event carries {@code agentTraceId}, which is not known until the trace exists.</li>
 *   <li><b>The promotion window's end.</b> spec-02 FR14's tool signature has no
 *       {@code effectiveTo}; spec-01 &sect;2.6 requires the event to carry one. It is taken from the
 *       batch's expiry through {@link BatchDetailsProvider}: a markdown meant to clear a batch
 *       before it expires has no work left to do afterwards. That is a policy, so it is decided in
 *       code and not asked of the model. spec-06 &sect;4 records it as a specification gap.</li>
 * </ol>
 *
 * <p><b>What is not here, and must never be.</b> No price. No margin arithmetic. No call to the
 * Pricing context. This service publishes a proposal and its reasoning; Pricing independently
 * decides whether to honour it (spec-01 &sect;1.5, spec-02 FR11). spec-06 &sect;7 states the rule
 * and names the test that enforces it mechanically over this whole source tree.
 *
 * <p><b>Not a Spring bean in Phase A.</b> It is constructed with plain arguments, deliberately: no
 * {@code @Value}, no field injection, no configuration read from the environment, so every
 * orchestration test builds one in a line with no Spring context and no broker. Its wiring arrives
 * in Phase B together with the adapter that supplies a {@link MarkdownDecisionAgent}, and with the
 * ruling on how the trigger is consumed (spec-06 &sect;2).
 *
 * <p>{@code clock} stamps {@code completedAt} on the trace and {@code occurredAt} on the event.
 * Both are wall-clock, and legitimately so: spec-01 &sect;2.6's event-time rule governs windowing
 * and the figures derived from it, and when a recommendation was made is a fact about this
 * service's own operation. Injecting the clock is what lets a test assert either of them.
 */
public class MarkdownAgentOrchestrator {

    /**
     * spec-06 &sect;5's second idempotency mechanism. Deriving the identifier from the trigger
     * makes a concurrent double delivery collide on the primary key rather than only on the
     * secondary unique constraint, and makes a trace traceable back to its trigger by inspection.
     */
    static final String TRACE_ID_PREFIX = "TRC-";

    /** spec-06 &sect;6: what the trace records when nothing was recommended. */
    private static final String NO_MARKDOWN = "NO_MARKDOWN";

    private final MarkdownDecisionAgent agent;
    private final AgentToolRegistry tools;
    private final AgentTraceStore traceStore;
    private final MarkdownRecommendationPublisher publisher;
    private final BatchDetailsProvider batchDetailsProvider;
    private final Clock clock;

    public MarkdownAgentOrchestrator(MarkdownDecisionAgent agent,
                                     AgentToolRegistry tools,
                                     AgentTraceStore traceStore,
                                     MarkdownRecommendationPublisher publisher,
                                     BatchDetailsProvider batchDetailsProvider,
                                     Clock clock) {
        this.agent = agent;
        this.tools = tools;
        this.traceStore = traceStore;
        this.publisher = publisher;
        this.batchDetailsProvider = batchDetailsProvider;
        this.clock = clock;
    }

    /**
     * FR14's use case, from trigger to trace.
     *
     * @param trigger the {@code sell-through-risk-detected} event that flagged a batch
     * @return the trace for this trigger &mdash; newly written, or the one a previous delivery of
     *         the same {@code eventId} already produced
     */
    public AgentTraceRecord onRiskDetected(SellThroughRiskDetectedEvent trigger) {
        String triggerEventId = trigger.eventId().toString();

        // spec-06 s5, mechanism one: the ordinary redelivery, answered without a constraint
        // violation. A redelivery deliberates again about nothing and publishes nothing.
        Optional<AgentTraceRecord> alreadyHandled = traceStore.findByTriggerEventId(triggerEventId);
        if (alreadyHandled.isPresent()) {
            return alreadyHandled.get();
        }

        AgentDeliberation deliberation = agent.deliberate(trigger, tools);
        Instant completedAt = clock.instant();

        MarkdownProposal proposal = deliberation.proposal();
        Instant effectiveTo = proposal == null ? null : promotionEnds(trigger);
        boolean recommends = proposal != null && effectiveTo != null;

        AgentTraceRecord trace = new AgentTraceRecord(
                TRACE_ID_PREFIX + triggerEventId,
                triggerEventId,
                trigger.batchId(),
                List.copyOf(deliberation.toolCalls()),
                recommends ? summarise(proposal, effectiveTo) : NO_MARKDOWN,
                deliberation.rationale(),
                completedAt);

        // Written whether or not anything was proposed: the trace is the audit record of the
        // decision, and a decision to leave a batch alone is a decision (spec-06 s5).
        traceStore.save(trace);

        if (recommends) {
            publisher.publish(new MarkdownRecommendedEvent(
                    UUID.randomUUID(),
                    completedAt,
                    trigger.batchId(),
                    trigger.sku(),
                    proposal.discountTier(),
                    proposal.effectiveFrom(),
                    effectiveTo,
                    proposal.rationale(),
                    trace.agentTraceId()));
        }

        return trace;
    }

    /**
     * The promotion window's end, derived from the batch rather than asked of the model: a
     * markdown meant to clear a batch before it expires has no work left to do afterwards
     * (spec-06 &sect;4, deviation D-3).
     *
     * <p>{@code null} when the batch cannot be resolved. The agent will already have been told
     * {@code error=BATCH_NOT_FOUND}, and spec-01 &sect;2.6 Table A requires {@code effectiveTo} on
     * the event, so there is no contract-complete recommendation to publish and the trace records
     * that none was made. Flagged in the Phase B1 log as a specification gap.
     */
    private Instant promotionEnds(SellThroughRiskDetectedEvent trigger) {
        return batchDetailsProvider.findBatch(trigger.batchId())
                .map(BatchDetails::expiryAt)
                .orElse(null);
    }

    /** spec-06 &sect;6's proposed form: {@code <TIER> from <effectiveFrom> until <effectiveTo>}. */
    private static String summarise(MarkdownProposal proposal, Instant effectiveTo) {
        return proposal.discountTier().name()
                + " from " + proposal.effectiveFrom()
                + " until " + effectiveTo;
    }
}
