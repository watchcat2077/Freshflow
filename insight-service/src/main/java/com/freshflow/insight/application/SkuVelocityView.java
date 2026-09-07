package com.freshflow.insight.application;

import java.time.Instant;

/**
 * spec-03 &sect;4 &mdash; {@code SkuVelocityView}, materialised by Kafka Streams. The CQRS read
 * model behind {@code GET /api/insight/velocity/&#123;sku&#125;} (FR12, spec-04).
 *
 * <p>Declared in {@code application}, not {@code domain}: spec-01 &sect;2.3 gives this service no
 * domain package at all. It owns no transactional business data, so it has no domain model to put
 * this in and no business invariant to enforce over it (&sect;2.5).
 *
 * <p>{@code windowStart} and {@code windowEnd} are <b>event-time</b> bounds, taken from the
 * producer-assigned {@code occurredAt} of the {@code item-sold} events that fell in the window.
 * spec-01 &sect;2.6 requires this and forbids using Kafka ingestion time.
 *
 * <p>{@code baselineVelocity} and {@code velocityRatio} are boxed and may be null, which is the
 * one design decision in this record worth stating. FR12 compares the current rate "against a
 * stored velocity baseline"; where no baseline is stored for a SKU there is no comparison to
 * report, and spec-04's {@code VelocityResponse} marks no property required. Reporting a baseline
 * of zero instead would be a silent zero of exactly the kind FR6a forbids elsewhere in this
 * system, and a ratio computed from it would be meaningless or infinite.
 */
public record SkuVelocityView(String sku,
                              Instant windowStart,
                              Instant windowEnd,
                              long quantitySold,
                              double velocityPerHour,
                              Double baselineVelocity,
                              Double velocityRatio) {
}
