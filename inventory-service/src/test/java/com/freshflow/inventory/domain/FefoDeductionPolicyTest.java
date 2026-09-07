package com.freshflow.inventory.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR2's FEFO rule, tested where it lives: the domain service (spec-03 §1 — "selects which batches
 * to deduct from across multiple batches of the same SKU. Spans multiple aggregates, therefore
 * does not belong to any single one").
 *
 * <p>No Spring, no broker, no database. {@link FefoDeductionPolicy} is a POJO and this is a plain
 * JUnit test, which is the practical dividend of spec-01 §2.3's insistence that domain classes
 * import nothing.
 *
 * <p><b>Expected to FAIL when written</b> — the policy throws
 * {@code UnsupportedOperationException} until Phase 4.
 */
@DisplayName("FR2: FEFO deduction policy")
class FefoDeductionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final Sku SKU = Sku.of("SKU-YOG-1KG");

    private FefoDeductionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new FefoDeductionPolicy();
    }

    /** A batch expiring {@code hoursFromNow} from NOW, with {@code remaining} units left. */
    private StockBatch batch(String id, int remaining, long hoursFromNow) {
        return StockBatch.rehydrate(
                BatchId.of(id), SKU,
                Quantity.of(100), Quantity.of(remaining),
                ExpiryDate.of(NOW.plus(hoursFromNow, ChronoUnit.HOURS)),
                Money.of(new BigDecimal("2.10"), "AUD"),
                BatchStatus.ACTIVE,
                NOW.minus(24, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("draws from the batch that expires soonest")
    void drawsFromTheSoonestExpiringBatch() {
        StockBatch soonest = batch("BAT-SOON", 10, 6);
        StockBatch later = batch("BAT-LATER", 10, 48);

        DeductionPlan plan = policy.planDeduction(List.of(soonest, later), Quantity.of(4), NOW);

        assertThat(plan.allocations()).hasSize(1);
        assertThat(plan.allocations().get(0).batch().batchId()).isEqualTo(BatchId.of("BAT-SOON"));
        assertThat(plan.allocations().get(0).quantity()).isEqualTo(Quantity.of(4));
        assertThat(plan.isFullyFulfilled()).isTrue();
    }

    /**
     * The case that makes FEFO more than an ordering preference: one sale spanning two batches.
     * Six units against a batch holding four must take all four from the soonest-expiring batch
     * and only the remaining two from the next.
     */
    @Test
    @DisplayName("spans batches in expiry order when one batch cannot cover the sale")
    void spansBatchesInExpiryOrder() {
        StockBatch soonest = batch("BAT-SOON", 4, 6);
        StockBatch later = batch("BAT-LATER", 10, 48);

        DeductionPlan plan = policy.planDeduction(List.of(soonest, later), Quantity.of(6), NOW);

        assertThat(plan.allocations()).hasSize(2);
        assertThat(plan.allocations().get(0).batch().batchId()).isEqualTo(BatchId.of("BAT-SOON"));
        assertThat(plan.allocations().get(0).quantity()).isEqualTo(Quantity.of(4));
        assertThat(plan.allocations().get(1).batch().batchId()).isEqualTo(BatchId.of("BAT-LATER"));
        assertThat(plan.allocations().get(1).quantity()).isEqualTo(Quantity.of(2));
        assertThat(plan.isFullyFulfilled()).isTrue();
    }

    /**
     * An expired batch is not sellable stock. Planning a deduction from it would plan something
     * {@link StockBatch#deduct} is bound to reject.
     */
    @Test
    @DisplayName("skips a batch that has already expired, however soon it expired")
    void skipsExpiredBatches() {
        StockBatch expired = batch("BAT-EXPIRED", 10, -1);
        StockBatch usable = batch("BAT-USABLE", 10, 48);

        DeductionPlan plan = policy.planDeduction(List.of(expired, usable), Quantity.of(3), NOW);

        assertThat(plan.allocations()).hasSize(1);
        assertThat(plan.allocations().get(0).batch().batchId()).isEqualTo(BatchId.of("BAT-USABLE"));
    }

    /**
     * FR2: "Stock on hand must never become negative." The sale is a fact that already happened,
     * so the policy allocates everything available and reports the remainder rather than
     * inventing stock to cover it.
     */
    @Test
    @DisplayName("allocates all available stock and reports the shortfall, never a negative plan")
    void reportsShortfallRatherThanOverAllocating() {
        StockBatch only = batch("BAT-ONLY", 3, 6);

        DeductionPlan plan = policy.planDeduction(List.of(only), Quantity.of(10), NOW);

        assertThat(plan.allocations()).hasSize(1);
        assertThat(plan.allocations().get(0).quantity()).isEqualTo(Quantity.of(3));
        assertThat(plan.unfulfilled()).isEqualTo(Quantity.of(7));
        assertThat(plan.isFullyFulfilled()).isFalse();
    }

    @Test
    @DisplayName("plans nothing when the SKU has no active batches")
    void plansNothingWhenThereIsNoStock() {
        DeductionPlan plan = policy.planDeduction(List.of(), Quantity.of(5), NOW);

        assertThat(plan.allocations()).isEmpty();
        assertThat(plan.unfulfilled()).isEqualTo(Quantity.of(5));
    }
}
