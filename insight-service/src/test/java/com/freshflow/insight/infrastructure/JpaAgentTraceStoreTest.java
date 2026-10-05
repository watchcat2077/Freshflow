package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.AgentTraceStore;
import com.freshflow.insight.application.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR14's trace persistence &mdash; a {@code @DataJpaTest} slice over the real H2 instance spec-01
 * &sect;1.3 gives this service. Written BEFORE the implementation, so <b>expected to FAIL</b>:
 * {@code JpaAgentTraceStore} and {@code AgentTraceMapper} throw
 * {@code UnsupportedOperationException("FR14 Phase B")}.
 *
 * <p>An agent trace is the only thing this service persists. Its two other read models are
 * materialised by the Kafka Streams topology and read back through Interactive Query; a trace is
 * not derived from any stream, is written once when a decision is made, and must survive a restart.
 *
 * <p><b>What this slice is for, and what it is not for.</b> The orchestration tests already assert
 * every rule about traces against an in-memory store, and they should: those are statements about
 * the use case. What only a database can answer is whether the ordering survives the round trip
 * &mdash; whether {@code sequence} is a stored property or an accident of insertion order that H2
 * happens to reproduce and a real database would not.
 *
 * <p>The store is constructed directly from the autowired repository rather than injected, because
 * it is an adapter behind a port and its constructor takes its collaborator as an argument. There
 * is nothing to wire.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("FR14: agent trace persistence (spec-06 s6)")
class JpaAgentTraceStoreTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-09-10T09:30:00Z");
    private static final String TRIGGER_EVENT_ID = "0a5f2f3e-9c31-4a7d-9b52-6d4f1f9d2c77";
    private static final String TRACE_ID = "TRC-" + TRIGGER_EVENT_ID;

    @Autowired
    private AgentTraceJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * The calls are handed in deliberately out of order &mdash; 3, 1, 2 &mdash; and must come back
     * as 1, 2, 3.
     *
     * <p>Saving them in order and reading them back in order would prove nothing: any storage at
     * all would pass, including one that ignored {@code sequence} entirely and returned rows in
     * insertion order. Shuffling the input is what makes the assertion about the stored sequence
     * rather than about the database's happening to be tidy.
     */
    @Test
    @DisplayName("a trace round-trips with its tool calls in ascending sequence order")
    void trace_roundTripsWithToolCallsInAscendingSequenceOrder() {
        AgentTraceStore store = new JpaAgentTraceStore(repository);

        AgentTraceRecord trace = new AgentTraceRecord(
                TRACE_ID,
                TRIGGER_EVENT_ID,
                "BAT-0001",
                List.of(
                        new ToolCall(3, "proposeMarkdown", "BAT-0001,PERCENT_30",
                                "recorded", COMPLETED_AT),
                        new ToolCall(1, "getBatchDetails", "BAT-0001",
                                "remainingQuantity=41", COMPLETED_AT),
                        new ToolCall(2, "getSellThroughRate", "SKU-YOG-1KG",
                                "velocityPerHour=120.0", COMPLETED_AT)),
                "PERCENT_30 from 2026-09-10T09:30:00Z until 2026-09-10T18:30:00Z",
                "Remaining quantity will not clear before expiry at the current rate.",
                COMPLETED_AT);

        store.save(trace);
        entityManager.flush();
        entityManager.clear();

        Optional<AgentTraceRecord> found = store.findByAgentTraceId(TRACE_ID);

        assertThat(found).isPresent();
        AgentTraceRecord reloaded = found.get();

        assertThat(reloaded.triggerEventId()).isEqualTo(TRIGGER_EVENT_ID);
        assertThat(reloaded.batchId()).isEqualTo("BAT-0001");
        assertThat(reloaded.finalRecommendation()).isEqualTo(trace.finalRecommendation());
        assertThat(reloaded.rationale()).isEqualTo(trace.rationale());
        assertThat(reloaded.completedAt()).isEqualTo(COMPLETED_AT);

        assertThat(reloaded.toolCalls())
                .as("spec-04 declares sequence on every tool call, so the order must be a stored "
                        + "property and not an artefact of how the rows were inserted")
                .extracting(ToolCall::sequence)
                .containsExactly(1, 2, 3);
        assertThat(reloaded.toolCalls())
                .extracting(ToolCall::toolName)
                .containsExactly("getBatchDetails", "getSellThroughRate", "proposeMarkdown");
        assertThat(reloaded.toolCalls())
                .extracting(ToolCall::arguments)
                .containsExactly("BAT-0001", "SKU-YOG-1KG", "BAT-0001,PERCENT_30");
        assertThat(reloaded.toolCalls())
                .extracting(ToolCall::result)
                .containsExactly("remainingQuantity=41", "velocityPerHour=120.0", "recorded");

        assertThat(store.findByTriggerEventId(TRIGGER_EVENT_ID))
                .as("the idempotency lookup of spec-01 s2.6 must find the trace it just stored")
                .map(AgentTraceRecord::agentTraceId)
                .contains(TRACE_ID);
    }

    /**
     * The FR14 counterpart of the briefing's long-text case (5 October 2026): a rationale and a
     * tool result are text the model writes or reads and have no natural upper bound, so the
     * {@code rationale}, {@code arguments} and {@code result} columns may not have one either.
     */
    @Test
    @DisplayName("a long rationale and long tool arguments and results round-trip unchanged")
    void trace_withLongRationaleAndLongToolCalls_roundTrips() {
        AgentTraceStore store = new JpaAgentTraceStore(repository);
        String longRationale = "With many units remaining the model explained itself at length. ".repeat(300); // ~19,000 chars
        String longArguments = "BAT-0001,PERCENT_30,2026-09-10T09:30:00Z," + "x".repeat(6_000);
        String longResult = "recorded batchId=BAT-0001 tier=PERCENT_30 " + "y".repeat(12_000);

        store.save(new AgentTraceRecord(TRACE_ID, TRIGGER_EVENT_ID, "BAT-0001",
                List.of(new ToolCall(1, "proposeMarkdown", longArguments, longResult, COMPLETED_AT)),
                "PERCENT_30 from 2026-09-10T09:30:00Z until 2026-09-10T18:30:00Z", longRationale, COMPLETED_AT));
        entityManager.flush();
        entityManager.clear();

        AgentTraceRecord reloaded = store.findByAgentTraceId(TRACE_ID).orElseThrow();
        assertThat(reloaded.rationale()).isEqualTo(longRationale);
        assertThat(reloaded.toolCalls()).hasSize(1);
        assertThat(reloaded.toolCalls().get(0).arguments()).isEqualTo(longArguments);
        assertThat(reloaded.toolCalls().get(0).result()).isEqualTo(longResult);
    }
}
