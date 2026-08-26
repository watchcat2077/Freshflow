package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.sales.application.SaleEventPublisher;
import com.freshflow.sales.domain.LineItem;
import com.freshflow.sales.domain.SaleTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Week 5 adapter for the {@code SaleEventPublisher} outbound port.
 *
 * <p>FR5: "Publishes one item-sold event per line item" &mdash; hence the loop rather than a single
 * transaction-level event. Each is translated into the {@code contracts} module's
 * {@link ItemSoldEvent}, the Published Language of spec-01 &sect;1.5, so this context's own types
 * never cross a boundary.
 *
 * <p>{@code occurredAt} is carried from the transaction rather than read from a clock at
 * publication time: spec-01 &sect;2.6 requires producer-assigned event time and forbids relying on
 * Kafka ingestion time, and FR5 requires every event to carry it. {@code eventId} is minted per
 * event so a Week 6 consumer can deduplicate on it.
 *
 * <p>No broker this week. The Kafka adapter is Week 6 and replaces this class behind the same
 * unchanged port.
 */
@Component
public class LoggingSaleEventPublisher implements SaleEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingSaleEventPublisher.class);

    @Override
    public void publishItemSold(SaleTransaction transaction) {
        for (LineItem item : transaction.lineItems()) {
            ItemSoldEvent payload = new ItemSoldEvent(
                    UUID.randomUUID(),
                    transaction.occurredAt(),
                    transaction.transactionId(),
                    item.sku().value(),
                    item.quantity(),
                    new MoneyPayload(item.unitPrice().amount(), item.unitPrice().currency()),
                    transaction.occurredAt());

            log.info("[event] topic={} eventId={} occurredAt={} transactionId={} sku={} quantity={} unitPrice={} {}",
                    ItemSoldEvent.TOPIC, payload.eventId(), payload.occurredAt(),
                    payload.transactionId(), payload.sku(), payload.quantity(),
                    payload.unitPrice().amount(), payload.unitPrice().currency());
        }
    }
}
