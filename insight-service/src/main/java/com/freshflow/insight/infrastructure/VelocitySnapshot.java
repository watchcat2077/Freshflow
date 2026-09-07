package com.freshflow.insight.infrastructure;

import java.time.Instant;

/**
 * One closed velocity window for one SKU, as it leaves the windowed aggregate and enters FR13's
 * stream-table join.
 *
 * <p>{@code windowEnd} is what the join uses as "now". It is event time by construction &mdash; the
 * boundary of a window placed by {@code occurredAt} &mdash; which is how FR13's time arithmetic
 * satisfies spec-01 &sect;2.6 without ever consulting a clock.
 */
public record VelocitySnapshot(String sku, Instant windowStart, Instant windowEnd,
                               long quantitySold, double velocityPerHour) {
}
