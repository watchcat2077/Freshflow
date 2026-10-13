package com.freshflow.inventory.application;

import java.time.Instant;

/**
 * Command (spec-01 s2.1, L5 slide 7: a simple DTO handled in the application service layer, which does not enter
 * the domain layer) &mdash; apply one {@code item-sold} event to this context's stock.
 *
 * <p>Primitive-typed on purpose: it carries what arrived over the wire, in the upstream context's
 * terms, before translation into this context's value objects. The handler does that translation,
 * which is where Inventory's Conformist relationship to Sales (spec-01 &sect;1.5) actually lives.
 *
 * <p>It deliberately carries no {@code eventId}. Deduplicating redelivered messages is a property
 * of the inbound adapter, not of the use case: "apply this sale" is the same use case whether it
 * arrived over Kafka, over REST, or from a test.
 *
 * <p>{@code occurredAt} is the sale's producer-assigned event time. It is used to decide which
 * batches have expired, and it is carried onto the resulting {@code stock-level-changed} events
 * (spec-01 &sect;2.6).
 */
public record ApplyItemSoldCommand(String sku, Integer quantity, Instant occurredAt) {
}
