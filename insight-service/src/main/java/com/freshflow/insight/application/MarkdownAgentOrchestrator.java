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
 *   <li><b>The scope of a proposal.</b> A proposal for a batch other than the trigger's is refused
 *       and recorded as declined (spec-06 &sect;4.3, team ruling R7). The tool cannot make that
 *       check because it has no trigger; this class is the only object holding both values. It is
 *       a second boundary beside R5 &mdash; R5 says the agent may not write prices, R7 says it may
 *       not propose outside the batch it was woken for.</li>
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

    /**
     * spec-06 &sect;6, team ruling R9 &mdash; the agent proposed, and the proposal could not be
     * turned into a recommendation because the batch could not be resolved.
     *
     * <p>It exists because {@code NO_MARKDOWN} was answering two different questions with one word.
     * spec-06 &sect;5's reason for writing a trace at all is that "the question a supervisor
     * actually asks is why nothing was done about a flagged batch"; recording "decided not to" and
     * "tried and could not" as the same value cannot answer it. The distinction costs one constant
     * and is the difference between an audit record and a tally.
     */
    private static final String PROPOSED_BUT_UNRESOLVABLE = "PROPOSED_BUT_UNRESOLVABLE";

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

        // spec-06 s4.3, team ruling R7: a proposal for a batch other than the one this run was
        // woken for is refused here and recorded as declined. The tool cannot make this check --
        // it has no trigger -- and the orchestrator is the only object that holds both values.
        MarkdownProposal proposal = inScope(deliberation.proposal(), trigger);
        Instant effectiveTo = proposal == null ? null : promotionEnds(trigger);
        boolean recommends = proposal != null && effectiveTo != null;

        AgentTraceRecord trace = new AgentTraceRecord(
                TRACE_ID_PREFIX + triggerEventId,
                triggerEventId,
                trigger.batchId(),
                List.copyOf(deliberation.toolCalls()),
                finalRecommendation(proposal, effectiveTo),
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
     * the event, so there is no contract-complete recommendation to publish. The trace then records
     * {@code PROPOSED_BUT_UNRESOLVABLE} rather than {@code NO_MARKDOWN} &mdash; team ruling R9, on
     * the gap Phase B1 flagged.
     */
    private Instant promotionEnds(SellThroughRiskDetectedEvent trigger) {
        return batchDetailsProvider.findBatch(trigger.batchId())
                .map(BatchDetails::expiryAt)
                .orElse(null);
    }

    /**
     * The proposal, if it is for the batch this run was woken for; {@code null} otherwise
     * (spec-06 &sect;4.3, team ruling R7, 11 September 2026).
     *
     * <p><b>This is the second boundary the agent may not cross, and it stands beside R5.</b> R5
     * says the agent may not write a price; R7 says it may not propose outside the batch it was
     * triggered for. Both are enforced in ordinary code on this side of the port, because both are
     * statements about what the system does with a model's output rather than requests to the
     * model to behave.
     *
     * <p>spec-06 &sect;4.3 sites the check in {@code proposeMarkdown}, and Phase B1 found that the
     * tool cannot make it: {@link AgentToolRegistry} has no trigger, and its constructor is pinned
     * by tests. So the tool checks what it can &mdash; that the batch exists &mdash; and the scope
     * check is here, in the one object that holds both values. That is the same argument spec-06
     * &sect;5 used to move publication out of the tool, applied a second time: the tool stays
     * stateless, the orchestrator holds the trigger context.
     *
     * <p>Recorded as declined, per the ruling. The trace still carries the agent's own rationale
     * and the {@code proposeMarkdown} call it made, so the refusal is visible in the audit record
     * rather than silent.
     */
    private static MarkdownProposal inScope(MarkdownProposal proposal,
                                            SellThroughRiskDetectedEvent trigger) {
        if (proposal == null) {
            return null;
        }
        return trigger.batchId() != null && trigger.batchId().equals(proposal.batchId())
                ? proposal
                : null;
    }

    /**
     * spec-06 &sect;6's machine-readable summary of the outcome, in its three cases.
     *
     * <table>
     *   <caption>Outcomes</caption>
     *   <tr><th>Outcome</th><th>Value</th></tr>
     *   <tr><td>Proposed, in scope, batch resolved</td>
     *       <td>{@code <TIER> from <effectiveFrom> until <effectiveTo>}</td></tr>
     *   <tr><td>Proposed, but the batch could not be resolved</td>
     *       <td>{@code PROPOSED_BUT_UNRESOLVABLE} (R9)</td></tr>
     *   <tr><td>Declined, or proposed out of scope (R7)</td><td>{@code NO_MARKDOWN}</td></tr>
     * </table>
     */
    private static String finalRecommendation(MarkdownProposal proposal, Instant effectiveTo) {
        if (proposal == null) {
            return NO_MARKDOWN;
        }
        if (effectiveTo == null) {
            return PROPOSED_BUT_UNRESOLVABLE;
        }
        return proposal.discountTier().name()
                + " from " + proposal.effectiveFrom()
                + " until " + effectiveTo;
    }
}
