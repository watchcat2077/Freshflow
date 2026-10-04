package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.AgentTraceStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The day-range read FR15 added to {@code AgentTraceStore}, against the real H2 instance. Written
 * BEFORE the implementation, so <b>expected to FAIL</b>:
 * {@code JpaAgentTraceStore.findCompletedBetween} throws {@code UnsupportedOperationException}.
 *
 * <p>A separate file from {@code JpaAgentTraceStoreTest}, which is FR14 Phase A's and is left as
 * it was. What only the database can answer here is that the two bounds are applied with the
 * right inclusivity by the query Spring Data derives from the method name &mdash; an in-memory
 * store that gets it right proves nothing about the SQL.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("FR15: agent traces completed within a UTC day")
class JpaAgentTraceStoreDayRangeTest {

    @Autowired
    private AgentTraceJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("findCompletedBetween is inclusive of the start, exclusive of the end, in order")
    void findCompletedBetween_appliesBothBoundsAndOrdersByCompletion() {
        AgentTraceStore store = new JpaAgentTraceStore(repository);

        store.save(trace("TRC-LATE", "2026-10-04T17:00:00Z"));
        store.save(trace("TRC-NEXT-DAY", "2026-10-05T00:00:00Z"));
        store.save(trace("TRC-EARLY", "2026-10-04T09:30:00Z"));
        store.save(trace("TRC-DAY-BEFORE", "2026-10-03T23:59:59Z"));
        store.save(trace("TRC-MIDNIGHT", "2026-10-04T00:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        List<AgentTraceRecord> day = store.findCompletedBetween(
                Instant.parse("2026-10-04T00:00:00Z"), Instant.parse("2026-10-05T00:00:00Z"));

        assertThat(day)
                .extracting(AgentTraceRecord::agentTraceId)
                .containsExactly("TRC-MIDNIGHT", "TRC-EARLY", "TRC-LATE");
    }

    private static AgentTraceRecord trace(String traceId, String completedAt) {
        return new AgentTraceRecord(traceId, "EVT-" + traceId, "BAT-" + traceId, List.of(),
                "NO_MARKDOWN", "scripted", Instant.parse(completedAt));
    }
}
