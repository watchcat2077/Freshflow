package com.freshflow.insight.application;

import java.time.Instant;

/**
 * One window of the Kafka Streams aggregate: how many units of a SKU sold between
 * {@code windowStart} and {@code windowEnd}.
 *
 * <p>Both bounds are event time, derived from the producer-assigned {@code occurredAt} of the
 * {@code item-sold} events (spec-01 &sect;2.6).
 *
 * <p>This is what {@link VelocityWindowStore} hands back &mdash; the raw aggregate, with no
 * judgement applied about which window is the current one. That judgement belongs to
 * {@link VelocityQueryService}, so that it can be exercised without a broker.
 */
public record WindowedQuantity(Instant windowStart, Instant windowEnd, long quantitySold) {
}
