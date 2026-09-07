package com.freshflow.inventory.domain;

import java.util.List;

/**
 * What {@link FefoDeductionPolicy} decided: which batches to draw a sale from, in order, and how
 * much of the sale could not be satisfied at all.
 *
 * <p>A plan rather than a mutation. The policy spans several aggregates and therefore may not
 * reach inside any of them; it says what should happen, and each {@link StockBatch} then enforces
 * its own invariants when {@link StockBatch#deduct} is called with the allocated quantity. That
 * split is what keeps "stock must never go negative" inside the aggregate that owns the number,
 * where spec-01 &sect;2.5 requires it, while FEFO selection lives in the domain service that spans
 * them (spec-03 &sect;1).
 *
 * @param allocations  batches to deduct from, in the order the policy chose
 * @param unfulfilled  units of the sale that no batch could supply; zero in the ordinary case
 */
public record DeductionPlan(List<Allocation> allocations, Quantity unfulfilled) {

    public DeductionPlan {
        allocations = List.copyOf(allocations);
    }

    /** True when some of the sale could not be drawn from any batch. */
    public boolean isFullyFulfilled() {
        return unfulfilled.isZero();
    }

    /** One batch and the number of units to take from it. */
    public record Allocation(StockBatch batch, Quantity quantity) {
    }
}
