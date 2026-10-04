package com.freshflow.insight.application;

import com.freshflow.insight.domain.BatchRiskView;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;

/**
 * The evidence FR15's unit tests brief against: two flagged batches and one safe one, five FR14
 * traces spread across three UTC days, and one SKU with a current velocity.
 *
 * <p>Shared by {@code BriefingToolRegistryTest} and {@code BriefingOrchestratorTest} so that the
 * tool a model reads and the count the orchestrator computes are asserted against the same facts.
 *
 * <p>The traces are the point. On the business date, {@link #TODAY}, FR14 completed three runs:
 * one that was published ({@code TRC-A}), one that declined ({@code TRC-B}, {@code NO_MARKDOWN})
 * and one that proposed but could not be resolved ({@code TRC-E},
 * {@code PROPOSED_BUT_UNRESOLVABLE}). The tool must list all three; the orchestrator must count
 * one. Two more sit exactly on the day's boundaries: {@code TRC-D} at the last second of the day
 * before, and {@code TRC-C} at the first instant of the day after. Neither belongs to
 * {@link #TODAY}, and an off-by-one on either bound would pull one of them in.
 */
final class BriefingTestFixtures {

    static final Instant NOW = Instant.parse("2026-10-04T18:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    static final String SKU = "SKU-YOG-1KG";

    static final String PUBLISHED_A =
            "PERCENT_30 from 2026-10-04T09:30:00Z until 2026-10-04T18:30:00Z";
    static final String PUBLISHED_C =
            "PERCENT_50 from 2026-10-05T00:00:00Z until 2026-10-05T12:00:00Z";
    static final String PUBLISHED_D =
            "PERCENT_20 from 2026-10-03T23:59:59Z until 2026-10-04T08:00:00Z";

    private BriefingTestFixtures() {
    }

    /** Two flagged batches, BAT-WORST ahead of BAT-MILD, and a safe one the list must omit. */
    static RiskQueryService riskQueries() {
        Instant detectedAt = Instant.parse("2026-10-04T17:59:00Z");
        List<BatchRiskView> views = List.of(
                new BatchRiskView("BAT-MILD", SKU, 20, 10, 1.0, 10.0, 10.0, detectedAt, true),
                new BatchRiskView("BAT-WORST", SKU, 40, 10, 2.0, 20.0, 20.0, detectedAt, true),
                new BatchRiskView("BAT-SAFE", SKU, 40, 10, 5.0, 50.0, -10.0, detectedAt, false));
        return new RiskQueryService(() -> views);
    }

    static RiskQueryService noRisks() {
        return new RiskQueryService(List::of);
    }

    /** Five traces, saved in a deliberately non-chronological order. */
    static InMemoryAgentTraceStore traces() {
        InMemoryAgentTraceStore store = new InMemoryAgentTraceStore();
        store.save(trace("TRC-B", "BAT-B", "NO_MARKDOWN",
                "Sell-through recovered; no markdown warranted.", "2026-10-04T17:00:00Z"));
        store.save(trace("TRC-A", "BAT-A", PUBLISHED_A,
                "41 units will not clear before expiry at 2.0/h.", "2026-10-04T09:30:00Z"));
        store.save(trace("TRC-E", "BAT-E", "PROPOSED_BUT_UNRESOLVABLE",
                "Proposed, but the batch could not be resolved.", "2026-10-04T12:00:00Z"));
        store.save(trace("TRC-C", "BAT-C", PUBLISHED_C,
                "Next day's first decision.", "2026-10-05T00:00:00Z"));
        store.save(trace("TRC-D", "BAT-D", PUBLISHED_D,
                "Previous day's last decision.", "2026-10-03T23:59:59Z"));
        return store;
    }

    /**
     * A real {@link VelocityQueryService} over stub stores, as FR14's tests build one: two units in
     * the last sixty seconds is 120 an hour, against a baseline of 120, so the ratio is 1.0.
     */
    static VelocityQueryService velocityQueries() {
        List<WindowedQuantity> windows = List.of(new WindowedQuantity(NOW.minusSeconds(60), NOW, 2));
        return new VelocityQueryService(
                (sku, from, to) -> SKU.equals(sku) ? windows : List.of(),
                sku -> SKU.equals(sku) ? OptionalDouble.of(120.0) : OptionalDouble.empty(),
                CLOCK,
                60);
    }

    static BriefingToolRegistry registry() {
        return new BriefingToolRegistry(riskQueries(), traces(), velocityQueries());
    }

    private static AgentTraceRecord trace(String traceId, String batchId, String outcome,
                                          String rationale, String completedAt) {
        return new AgentTraceRecord(traceId, "EVT-" + traceId, batchId, List.of(), outcome,
                rationale, Instant.parse(completedAt));
    }
}
