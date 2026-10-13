/**
 * Published Language (spec-01 &sect;1.5) &mdash; the shared event contract between bounded
 * contexts. Every event defined here carries {@code eventId} (UUID, for idempotent
 * consumption) and {@code occurredAt} (producer-assigned event time, used for windowing)
 * per spec-01 &sect;2.6.
 *
 * <p>Holds one record per topic in spec-01 &sect;2.6 Table A, the {@code IntegrationEvent}
 * interface they all implement, and the shared payload types {@code MoneyPayload} and
 * {@code DiscountTier}. Nothing here depends on Spring, Kafka or any service module.
 */
package com.freshflow.contracts;
