package com.freshflow.inventory.domain;

import java.time.Instant;

/**
 * Domain event (spec-01 &sect;2.4) &mdash; what is left of a batch changed.
 *
 * <p>The internal form, in this context's own value objects; the infrastructure adapter
 * translates it into the {@code stock-level-changed} integration event of the {@code contracts}
 * module (spec-01 &sect;2.6, Table A).
 *
 * <p>{@code changedAt} is the {@code occurredAt} of the sale that caused the deduction, carried
 * through rather than read from a clock at deduction time. That matters more than it looks: a
 * sale that reaches this service late still describes a stock change that happened when the sale
 * happened, and FR13 windows on that instant.
 */
public record StockLevelChanged(BatchId batchId, Sku sku, Quantity remainingQuantity,
                                Instant changedAt) {
}
