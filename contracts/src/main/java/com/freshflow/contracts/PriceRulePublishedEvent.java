package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event for topic {@code price-rule-published} (spec-01 &sect;2.6).
 * Producer: pricing. Consumer: sales.
 *
 * <p>spec-01 &sect;2.6 makes this topic the source of {@code sales-service}'s price data.
 * {@code sales-service} materialises its local {@code PriceSnapshot} from it through the
 * {@code PriceSnapshotFeed} port (spec-01 &sect;1.3, ADR 0001); it never calls
 * {@code pricing-service} synchronously.
 *
 * <p>&sect;2.6 fixes the consumption rules for this topic, and the sales-side adapter implements
 * both: consumers MUST deduplicate on {@code eventId}, and MUST apply last-write-wins on
 * {@code occurredAt} per {@code sku}. {@code occurredAt} is producer-assigned event time; Kafka
 * ingestion time is never used for that.
 */
public record PriceRulePublishedEvent(UUID eventId,
                                      Instant occurredAt,
                                      String sku,
                                      MoneyPayload basePrice,
                                      MoneyPayload unitCost,
                                      String category) implements IntegrationEvent {

    public static final String TOPIC = "price-rule-published";
}
