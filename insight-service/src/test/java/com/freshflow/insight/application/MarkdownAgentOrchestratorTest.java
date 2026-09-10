package com.freshflow.insight.application;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.MarkdownRecommendedEvent;
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
 * FR14 orchestration &mdash; plain JUnit over the application layer, with no Spring context, no
 * broker, no database and above all no model. Written BEFORE the implementation (Continuous
 * Auto-Validation), so <b>these tests are expected to FAIL</b>: {@code MarkdownAgentOrchestrator}
 * is a signature surface whose methods throw {@code UnsupportedOperationException("FR14 Phase B")}.
 *
 * <p>What is under test is everything about FR14 that must hold regardless of which model sits
 * behind the {@code MarkdownDecisionAgent} port: that a trace is written, that its tool calls keep
 * their order, that exactly one recommendation crosses into Pricing, that a redelivered trigger
 * changes nothing, and that a decision not to act is recorded as carefully as a decision to act.
 * Every one of those is a property of ordinary code. None of them could be asserted at all if the
 * LLM were not behind a port &mdash; which is ADR 0004's argument, written out as a test file.
 *
 * <p>The clock is fixed at {@link #NOW}. FR14 produces two timestamps of its own, the trace's
 * {@code completedAt} and the event's {@code occurredAt}, and a test that cannot fix "now" cannot
 * assert either.
 */
@DisplayName("FR14: markdown agent orchestration (spec-06)")
class MarkdownAgentOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-09-10T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String BATCH_ID = "BAT-0001";
    private static final String SKU = "SKU-YOG-1KG";
    private static final UUID TRIGGER_EVENT_ID =
            UUID.fromString("0a5f2f3e-9c31-4a7d-9b52-6d4f1f9d2c77");

    /** Nine hours out, which is the situation spec-04's own markdown example describes. */
    private static final Instant EXPIRY_AT = NOW.plus(Duration.ofHours(9));

    private static final BatchDetails BATCH = new BatchDetails(BATCH_ID, SKU, 41, EXPIRY_AT,
            new MoneyPayload(new BigDecimal("2.10"), "AUD"));

    private final InMemoryAgentTraceStore traceStore = new InMemoryAgentTraceStore();
    private final RecordingMarkdownRecommendationPublisher publisher =
            new RecordingMarkdownRecommendationPublisher();
    private final StubBatchDetailsProvider batchDetails = new StubBatchDetailsProvider().with(BATCH);
    private final AgentToolRegistry tools = new AgentToolRegistry(batchDetails, velocityQueries());

    // ------------------------------------------------------------------------------- fixtures

    /**
     * A real {@link VelocityQueryService} over stub stores, not a mock of it. The
     * {@code getSellThroughRate} tool reads FR12 through this service, and FR12's window-selection
     * rule is the reason the service exists; a mocked service would test the tool against an
     * assumption about FR12 rather than against FR12.
     */
    private static VelocityQueryService velocityQueries() {
        List<WindowedQuantity> windows = List.of(
                new WindowedQuantity(NOW.minusSeconds(60), NOW, 2));
        return new VelocityQueryService(
                (sku, from, to) -> SKU.equals(sku) ? windows : List.of(),
                sku -> SKU.equals(sku) ? OptionalDouble.of(120.0) : OptionalDouble.empty(),
                CLOCK,
                60);
    }

    private static SellThroughRiskDetectedEvent trigger() {
        return new SellThroughRiskDetectedEvent(TRIGGER_EVENT_ID, NOW.minusSeconds(30),
                BATCH_ID, SKU, 41, 9, 2.0, 23.0);
    }

    private MarkdownAgentOrchestrator orchestratorWith(MarkdownDecisionAgent agent) {
        return new MarkdownAgentOrchestrator(agent, tools, traceStore, publisher, batchDetails,
                CLOCK);
    }

    // ------------------------------------------------------------------------------- the trace

    /**
     * FR14: "full tool-call trace persisted and queryable by {@code agentTraceId}".
     *
     * <p>The assertion carrying the weight is {@code sequence} 1, 2, 3 alongside the tool names in
     * the same order. spec-04 declares {@code sequence} on every call, so a consumer is entitled to
     * sort by it; a trace that recorded the right calls in the wrong order, or that left every
     * sequence at zero, would satisfy a size assertion and be useless as an audit record.
     */
    @Test
    @DisplayName("a risk event yields a persisted trace whose tool calls are in call order")
    void onRiskDetected_persistsTraceWithToolCallsInAscendingSequenceOrder() {
        orchestratorWith(StubMarkdownDecisionAgent.proposing(CLOCK)).onRiskDetected(trigger());

        assertThat(traceStore.all()).hasSize(1);
        AgentTraceRecord stored = traceStore.all().get(0);

        assertThat(stored.triggerEventId()).isEqualTo(TRIGGER_EVENT_ID.toString());
        assertThat(stored.batchId()).isEqualTo(BATCH_ID);
        assertThat(stored.completedAt()).isEqualTo(NOW);

        assertThat(stored.toolCalls())
                .extracting(ToolCall::sequence)
                .containsExactly(1, 2, 3);
        assertThat(stored.toolCalls())
                .extracting(ToolCall::toolName)
                .containsExactly(AgentToolRegistry.GET_BATCH_DETAILS,
                        AgentToolRegistry.GET_SELL_THROUGH_RATE,
                        AgentToolRegistry.PROPOSE_MARKDOWN);

        assertThat(stored.toolCalls()).allSatisfy(call -> {
            assertThat(call.arguments())
                    .as("a recorded call must carry what was asked, not merely that it happened")
                    .isNotBlank();
            assertThat(call.result())
                    .as("a recorded call must carry what the agent was told")
                    .isNotBlank();
            assertThat(call.timestamp()).isNotNull();
        });

        assertThat(traceStore.findByAgentTraceId(stored.agentTraceId())).contains(stored);
    }

    // ------------------------------------------------------------------------- the publication

    /**
     * The proposal leaves the Insight context exactly once, and carries the key that makes it
     * auditable.
     *
     * <p>{@code effectiveTo} is asserted against the batch's expiry deliberately. spec-02 FR14's
     * {@code proposeMarkdown} signature has no such argument and spec-01 &sect;2.6 requires the
     * event to carry one, so somebody has to decide it; spec-06 &sect;4 decides that a markdown
     * meant to clear a batch before it expires ends when the batch does, and this is where that
     * decision is pinned down.
     */
    @Test
    @DisplayName("a proposal publishes exactly one markdown-recommended, carrying the trace id")
    void onRiskDetected_whenAgentProposes_publishesExactlyOneRecommendation() {
        AgentTraceRecord trace = orchestratorWith(StubMarkdownDecisionAgent.proposing(CLOCK))
                .onRiskDetected(trigger());

        assertThat(publisher.published()).hasSize(1);
        MarkdownRecommendedEvent event = publisher.published().get(0);

        assertThat(event.agentTraceId())
                .as("the recommendation must point back at the trace that justifies it")
                .isEqualTo(trace.agentTraceId());
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.batchId()).isEqualTo(BATCH_ID);
        assertThat(event.sku()).isEqualTo(SKU);
        assertThat(event.discountTier()).isEqualTo(DiscountTier.PERCENT_30);
        assertThat(event.effectiveFrom()).isEqualTo(NOW);
        assertThat(event.effectiveTo()).isEqualTo(EXPIRY_AT);
        assertThat(event.rationale()).isEqualTo(StubMarkdownDecisionAgent.PROPOSAL_RATIONALE);

        assertThat(trace.rationale()).isEqualTo(event.rationale());
        assertThat(trace.finalRecommendation())
                .as("the trace's final recommendation must describe the event that was published")
                .contains(event.discountTier().name())
                .contains(event.effectiveFrom().toString())
                .contains(event.effectiveTo().toString());
    }

    // -------------------------------------------------------------------------- idempotency

    /**
     * spec-01 &sect;2.6: "{@code eventId} (UUID) &mdash; used for idempotent consumption".
     *
     * <p>At-least-once delivery is the normal case rather than the pathological one, so this is
     * what stops one flagged batch from producing two marked-down prices. Both halves are
     * asserted: one trace, because two traces for one decision would make the audit lie, and one
     * publication, because the Pricing context must not be asked twice.
     */
    @Test
    @DisplayName("the same trigger eventId delivered twice yields one trace and one publication")
    void onRiskDetected_whenSameTriggerDeliveredTwice_isIdempotent() {
        MarkdownAgentOrchestrator orchestrator =
                orchestratorWith(StubMarkdownDecisionAgent.proposing(CLOCK));

        AgentTraceRecord first = orchestrator.onRiskDetected(trigger());
        AgentTraceRecord second = orchestrator.onRiskDetected(trigger());

        assertThat(traceStore.all()).hasSize(1);
        assertThat(publisher.published()).hasSize(1);
        assertThat(second.agentTraceId())
                .as("a redelivery must find the existing trace, not mint a second identity")
                .isEqualTo(first.agentTraceId());
    }

    // ---------------------------------------------------------------------------- declining

    /**
     * The trace is the audit record, not a by-product of success.
     *
     * <p>An agent that looks at a flagged batch and decides against a markdown has made a decision,
     * and FR14 exists to make the agent's decisions explainable. If nothing were written here, the
     * only decisions on record would be the ones that acted, and the question a supervisor actually
     * asks &mdash; why was nothing done about that batch &mdash; would have no answer at all.
     */
    @Test
    @DisplayName("when the agent declines, the trace is still written and nothing is published")
    void onRiskDetected_whenAgentDeclines_persistsTraceAndPublishesNothing() {
        orchestratorWith(StubMarkdownDecisionAgent.declining(CLOCK)).onRiskDetected(trigger());

        assertThat(traceStore.all()).hasSize(1);
        AgentTraceRecord stored = traceStore.all().get(0);

        assertThat(stored.toolCalls())
                .extracting(ToolCall::toolName)
                .containsExactly(AgentToolRegistry.GET_BATCH_DETAILS,
                        AgentToolRegistry.GET_SELL_THROUGH_RATE);
        assertThat(stored.rationale()).isEqualTo(StubMarkdownDecisionAgent.DECLINED_RATIONALE);
        assertThat(stored.finalRecommendation())
                .as("spec-06 s6: a declined run records that no markdown was recommended")
                .isEqualTo("NO_MARKDOWN");

        assertThat(publisher.published()).isEmpty();
    }

    // ------------------------------------------------------------------------ the stub itself

    /**
     * The stub is the control in every experiment above, so its determinism is worth asserting
     * rather than assuming. Were it not deterministic, none of the other tests would be either, and
     * their failures would be unreadable.
     */
    @Test
    @DisplayName("the stub agent is deterministic: the same trigger yields the same tool calls")
    void stubAgent_isDeterministic() {
        StubMarkdownDecisionAgent agent = StubMarkdownDecisionAgent.proposing(CLOCK);

        AgentDeliberation first = agent.deliberate(trigger(), tools);
        AgentDeliberation second = agent.deliberate(trigger(), tools);

        assertThat(second.toolCalls()).isEqualTo(first.toolCalls());
        assertThat(second.proposal()).isEqualTo(first.proposal());
        assertThat(second.rationale()).isEqualTo(first.rationale());
    }
}
