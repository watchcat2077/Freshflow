package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event for topic {@code batch-received} (spec-01 &sect;2.6).
 * Producer: inventory. Consumer: insight.
 *
 * <p>Part of the Published Language (spec-01 &sect;1.5). Carries {@code eventId} for idempotent
 * consumption and {@code occurredAt} as producer-assigned event time, which is what windowing
 * uses; Kafka ingestion time is never used for that.
 *
 * <p>Produced by {@code inventory-service}'s {@code KafkaDomainEventPublisher} when a goods
 * receipt is registered (FR1), and folded into {@code insight-service}'s batch-state table, where
 * it supplies the expiry and opening quantity FR13 assesses against.
 */
public record BatchReceivedEvent(UUID eventId,
                                 Instant occurredAt,
                                 String batchId,
                                 String sku,
                                 int quantity,
                                 Instant expiryAt,
                                 MoneyPayload unitCost) implements IntegrationEvent {

    public static final String TOPIC = "batch-received";
}
