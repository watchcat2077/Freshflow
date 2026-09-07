package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event for topic {@code stock-level-changed} (spec-01 &sect;2.6, Table A).
 * Producer: inventory. Consumer: insight.
 *
 * <p>Emitted by FR2 once per batch whose remaining quantity changed as a result of a sale. One
 * {@code item-sold} event can therefore produce several of these, because FEFO may draw a single
 * sale from more than one batch.
 *
 * <p>Together with {@code batch-received} this is what FR13's live batch-state {@code KTable} is
 * built from: {@code batch-received} supplies a batch's SKU, expiry and opening quantity, and
 * this supplies every subsequent change to what is left of it. Neither is sufficient alone, which
 * is why FR2 is a precondition of FR13 rather than an independent requirement.
 *
 * <p>{@code changedAt} is the key field spec-01 &sect;2.6 names for this topic, and
 * {@code occurredAt} is the event time every event must carry. For this event they are the same
 * instant &mdash; the {@code occurredAt} of the sale that caused the deduction, carried through
 * rather than re-read from a clock, so that a late-arriving sale is still placed in the window it
 * happened in.
 */
public record StockLevelChangedEvent(UUID eventId,
                                     Instant occurredAt,
                                     String batchId,
                                     String sku,
                                     int remainingQuantity,
                                     Instant changedAt) implements IntegrationEvent {

    public static final String TOPIC = "stock-level-changed";
}
