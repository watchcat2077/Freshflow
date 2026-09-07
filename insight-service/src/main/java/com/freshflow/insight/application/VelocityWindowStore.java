package com.freshflow.insight.application;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port &mdash; read access to the windowed sell-through aggregate that the Kafka Streams
 * topology materialises (FR12).
 *
 * <p>Declared in {@code application} because spec-01 &sect;2.3's table says so for a service with
 * no domain package: "insight-service | No domain layer | application &mdash; there is no
 * {@code domain} package to declare them in". The adapter behind it lives in
 * {@code infrastructure} and uses Interactive Query, which is what spec-04 names for this
 * endpoint.
 *
 * <p>It deliberately returns <b>every</b> window overlapping the requested range rather than "the
 * current one". Choosing which window represents the velocity right now is a read-model decision,
 * and putting it in the adapter would bury it behind a state store that cannot be exercised
 * without a running broker.
 *
 * @param from lower bound on window <i>start</i> time, in event time
 * @param to   upper bound on window <i>start</i> time, in event time
 */
public interface VelocityWindowStore {

    List<WindowedQuantity> fetchWindows(String sku, Instant from, Instant to);
}
