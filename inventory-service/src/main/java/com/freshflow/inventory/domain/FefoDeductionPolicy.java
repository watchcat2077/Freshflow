package com.freshflow.inventory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service (spec-01 &sect;2.4, spec-03 &sect;1) &mdash; "selects which batches to deduct from
 * across multiple batches of the same SKU. Spans multiple aggregates, therefore does not belong to
 * any single one."
 *
 * <p>That sentence is the whole justification for this class existing. FEFO is a genuine business
 * rule, so spec-01 &sect;2.5 requires it to live in the domain layer; but it is a rule <i>about a
 * set of</i> {@link StockBatch} aggregates, and no single batch can see the others. A domain
 * service is the DDD building block for exactly that shape, and the alternative &mdash; putting
 * the loop in the application service &mdash; would move a business rule out of the domain.
 *
 * <p>A POJO with no Spring, no JPA and no import outside {@code java.*}. It is not a
 * {@code @Service}; the application layer constructs it.
 *
 * <p>It plans and does not mutate. See {@link DeductionPlan}.
 */
public final class FefoDeductionPolicy {

    /**
     * Chooses which batches supply a sale of {@code soldQuantity} units of one SKU.
     *
     * <p><b>First-expired-first-out</b>: draw from the batch that expires soonest, then the next,
     * until the sale is covered. That is the whole point of batch-level inventory in a fresh-food
     * system &mdash; selling the newest stock first is what produces the write-offs this project
     * exists to reduce.
     *
     * <p>Batches that have already expired are skipped rather than drawn from. An expired batch
     * is not sellable stock; {@link StockBatch#deduct} would reject it, and planning a deduction
     * the aggregate is bound to refuse would be planning a failure.
     *
     * @param batchesInExpiryOrder active batches for one SKU, soonest expiry first
     * @param soldQuantity         units sold
     * @param now                  the sale's event time, used to decide which batches have expired
     */
    public DeductionPlan planDeduction(List<StockBatch> batchesInExpiryOrder, Quantity soldQuantity,
                                       Instant now) {
        List<DeductionPlan.Allocation> allocations = new ArrayList<>();
        int outstanding = soldQuantity.value();

        for (StockBatch batch : batchesInExpiryOrder) {
            if (outstanding == 0) {
                break;
            }
            if (batch.isExpired(now) || batch.status() != BatchStatus.ACTIVE) {
                continue;
            }

            int available = batch.remainingQuantity().value();
            if (available == 0) {
                continue;
            }

            // Take as much as this batch can give, then move to the next-soonest.
            int taken = Math.min(available, outstanding);
            allocations.add(new DeductionPlan.Allocation(batch, Quantity.of(taken)));
            outstanding -= taken;
        }

        // Whatever is left over is a shortfall, not a negative allocation. The sale is a fact that
        // already happened; this service records what stock could cover, and says so.
        return new DeductionPlan(allocations, Quantity.of(outstanding));
    }
}
