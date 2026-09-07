package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event for topic {@code item-sold} (spec-01 &sect;2.6).
 * Producer: sales. Consumers: inventory, insight.
 *
 * <p>One event per line item, per FR5. {@code soldAt} is the key field spec-01 &sect;2.6 names for
 * this topic and {@code occurredAt} is the event time every event must carry; for this event they
 * are the same instant, the transaction's {@code occurredAt}, and both are assigned by the
 * producer rather than taken from the broker.
 */
public record ItemSoldEvent(UUID eventId,
                            Instant occurredAt,
                            String transactionId,
                            String sku,
                            int quantity,
                            MoneyPayload unitPrice,
                            Instant soldAt) implements IntegrationEvent {

    public static final String TOPIC = "item-sold";
}
