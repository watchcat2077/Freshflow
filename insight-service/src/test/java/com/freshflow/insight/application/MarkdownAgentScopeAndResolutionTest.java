package com.freshflow.insight.application;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two boundaries Phase B2 added to the orchestrator: R7's scope check and R9's third trace
 * value (FR14, spec-06 &sect;4.3 and &sect;6).
 *
 * <p>A separate file from {@code MarkdownAgentOrchestratorTest} because that one is Phase A's and
 * may not be edited. Nothing here weakens it; these are two cases it does not reach, both of them
 * raised as flags by Phase B1 and ruled on by the team on 11 September 2026.
 *
 * <p><b>Why either matters.</b> spec-06 &sect;5 says the trace exists because "the question a
 * supervisor actually asks is why nothing was done about a flagged batch". Both cases below are
 * runs where nothing was done, and in both the agent did in fact try. A trace that recorded them
 * identically to a deliberate decision not to act could not answer the question it was built for.
 */
@DisplayName("FR14: proposal scope (R7) and unresolvable batches (R9)")
class MarkdownAgentScopeAndResolutionTest {

    private static final Instant NOW = Instant.parse("2026-09-10T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String TRIGGER_BATCH_ID = "BAT-0001";
    private static final String OTHER_BATCH_ID = "BAT-0002";
    private static final String SKU = "SKU-YOG-1KG";
    private static final Instant EXPIRY_AT = NOW.plus(Duration.ofHours(9));

    private static final MoneyPayload UNIT_COST = new MoneyPayload(new BigDecimal("2.10"), "AUD");

    private final InMemoryAgentTraceStore traceStore = new InMemoryAgentTraceStore();
    private final RecordingMarkdownRecommendationPublisher publisher =
            new RecordingMarkdownRecommendationPublisher();

    // ------------------------------------------------------------------------------- R7: scope

    /**
     * <b>R7.</b> Both batches exist, so {@code proposeMarkdown} accepts the call &mdash; Phase B1's
     * deviation D-1 records that the tool can only check that a batch is one this service can see,
     * because {@code AgentToolRegistry} has no trigger. The refusal therefore has to happen in the
     * orchestrator, which is the only object holding both the trigger and the proposal.
     *
     * <p>The failure this prevents is not a wrong price: the orchestrator publishes the
     * <i>trigger's</i> batchId (B1-A4), so the wrong batch could never be marked down. It is an
     * audit record that disagrees with itself &mdash; a trace whose proposal names one batch and
     * whose published event names another.
     */
    @Test
    @DisplayName("a proposal for a different batch is refused, recorded as declined, and not published")
    void onRiskDetected_whenProposalNamesAnotherBatch_declinesAndPublishesNothing() {
        StubBatchDetailsProvider batches = new StubBatchDetailsProvider()
                .with(new BatchDetails(TRIGGER_BATCH_ID, SKU, 41, EXPIRY_AT, UNIT_COST))
                .with(new BatchDetails(OTHER_BATCH_ID, SKU, 12, EXPIRY_AT, UNIT_COST));

        AgentTraceRecord trace = orchestrator(batches, proposingFor(OTHER_BATCH_ID))
                .onRiskDetected(trigger());

        assertThat(publisher.published())
                .as("R7: the agent may not propose outside the batch it was woken for")
                .isEmpty();
        assertThat(trace.finalRecommendation())
                .as("the ruling records it as declined")
                .isEqualTo("NO_MARKDOWN");
        assertThat(trace.batchId())
                .as("the trace is about the batch that was flagged, not the one proposed for")
                .isEqualTo(TRIGGER_BATCH_ID);
        assertThat(trace.toolCalls())
                .as("what the agent attempted stays in the record; the refusal is visible, "
                        + "not silent")
                .extracting(ToolCall::toolName)
                .contains(AgentToolRegistry.PROPOSE_MARKDOWN);
        assertThat(traceStore.all()).hasSize(1);
    }

    // --------------------------------------------------------------- R9: proposed, unresolvable

    /**
     * <b>R9.</b> The agent proposed for the batch it was given, and the batch cannot be resolved,
     * so {@code effectiveTo} &mdash; which spec-01 &sect;2.6 Table A requires on the event &mdash;
     * has no source. Nothing contract-complete can be published.
     *
     * <p>Before the ruling this recorded {@code NO_MARKDOWN}, the same value as a deliberate
     * decision to leave the batch alone. The distinction between "decided not to" and "tried and
     * could not" is the whole of R9, and it costs one constant.
     */
    @Test
    @DisplayName("a proposal whose batch cannot be resolved records PROPOSED_BUT_UNRESOLVABLE")
    void onRiskDetected_whenBatchCannotBeResolved_recordsThatItWasProposedNotDeclined() {
        StubBatchDetailsProvider noBatches = new StubBatchDetailsProvider();

        AgentTraceRecord trace = orchestrator(noBatches, proposingFor(TRIGGER_BATCH_ID))
                .onRiskDetected(trigger());

        assertThat(publisher.published())
                .as("spec-01 s2.6 Table A requires effectiveTo, and there is no source for one")
                .isEmpty();
        assertThat(trace.finalRecommendation())
                .as("R9: 'decided not to' and 'tried and could not' are different answers to the "
                        + "question the trace exists to answer")
                .isEqualTo("PROPOSED_BUT_UNRESOLVABLE")
                .isNotEqualTo("NO_MARKDOWN");
        assertThat(trace.rationale())
                .as("the agent's own reasoning is still the trace's rationale")
                .isEqualTo(RATIONALE);
    }

    /**
     * The declining path still records {@code NO_MARKDOWN}. Asserted here because R9 is only
     * meaningful if the two values stay distinct, and a change that collapsed them would otherwise
     * announce itself nowhere.
     */
    @Test
    @DisplayName("declining still records NO_MARKDOWN, so the two outcomes stay distinguishable")
    void onRiskDetected_whenAgentDeclines_stillRecordsNoMarkdown() {
        StubBatchDetailsProvider batches = new StubBatchDetailsProvider()
                .with(new BatchDetails(TRIGGER_BATCH_ID, SKU, 41, EXPIRY_AT, UNIT_COST));

        AgentTraceRecord trace = orchestrator(batches, StubMarkdownDecisionAgent.declining(CLOCK))
                .onRiskDetected(trigger());

        assertThat(trace.finalRecommendation()).isEqualTo("NO_MARKDOWN");
        assertThat(publisher.published()).isEmpty();
    }

    // --------------------------------------------------------------------------------- fixtures

    private static final String RATIONALE = "Proposed from a scripted deliberation.";

    private MarkdownAgentOrchestrator orchestrator(StubBatchDetailsProvider batches,
                                                   MarkdownDecisionAgent agent) {
        AgentToolRegistry tools = new AgentToolRegistry(batches, velocityQueries());
        return new MarkdownAgentOrchestrator(agent, tools, traceStore, publisher, batches, CLOCK);
    }

    /**
     * An agent that calls {@code proposeMarkdown} through the real registry and then proposes for
     * whichever batch it was told to. Local rather than a mode on
     * {@code StubMarkdownDecisionAgent}, because that class is Phase A's and may not be edited.
     */
    private static MarkdownDecisionAgent proposingFor(String batchId) {
        return (trigger, tools) -> {
            String arguments = batchId + ",PERCENT_30," + NOW + "," + RATIONALE;
            String result = tools.invoke(AgentToolRegistry.PROPOSE_MARKDOWN, arguments);
            return new AgentDeliberation(
                    List.of(new ToolCall(1, AgentToolRegistry.PROPOSE_MARKDOWN, arguments, result,
                            NOW)),
                    new MarkdownProposal(batchId, DiscountTier.PERCENT_30, NOW, RATIONALE),
                    RATIONALE);
        };
    }

    private static VelocityQueryService velocityQueries() {
        List<WindowedQuantity> windows = List.of(new WindowedQuantity(NOW.minusSeconds(60), NOW, 2));
        return new VelocityQueryService(
                (sku, from, to) -> SKU.equals(sku) ? windows : List.of(),
                sku -> SKU.equals(sku) ? OptionalDouble.of(120.0) : OptionalDouble.empty(),
                CLOCK,
                60);
    }

    private static SellThroughRiskDetectedEvent trigger() {
        return new SellThroughRiskDetectedEvent(UUID.randomUUID(), NOW.minusSeconds(30),
                TRIGGER_BATCH_ID, SKU, 41, 9, 2.0, 23.0);
    }
}
