package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.sales.application.SaleEventPublisher;
import com.freshflow.sales.domain.LineItem;
import com.freshflow.sales.domain.SaleTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the {@code SaleEventPublisher} outbound port &mdash; publishes
 * {@code item-sold} to Kafka through Spring Cloud Stream (spec-01 &sect;1.1, &sect;2.6).
 *
 * <p>{@code TransactionApplicationService} &mdash; the transaction script &mdash; calls
 * {@code saleEventPublisher.publishItemSold(transaction)} and knows nothing of Kafka; any adapter
 * may stand behind the port.
 *
 * <p>FR5: "Publishes one {@code item-sold} event per line item" &mdash; hence the loop rather than
 * one transaction-level event. Each line item is translated into the {@code contracts} module's
 * {@link ItemSoldEvent}, so this context's own types never cross a boundary (spec-01 &sect;1.5).
 *
 * <p>{@code occurredAt} and {@code soldAt} are both carried from the transaction, never read from
 * a clock at publication time and never taken from broker ingestion time: spec-01 &sect;2.6
 * requires producer-assigned event time because {@code insight-service}'s windowing depends on it.
 * {@code eventId}
 * is minted per line item, so a consumer can deduplicate them independently.
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class KafkaSaleEventPublisher implements SaleEventPublisher {

    /**
     * Spring Cloud Stream binding name; its destination is configured in {@code application.yml}
     * as {@link ItemSoldEvent#TOPIC}.
     */
    static final String BINDING = "itemSold-out-0";

    private static final Logger log = LoggerFactory.getLogger(KafkaSaleEventPublisher.class);

    private final StreamBridge streamBridge;

    public KafkaSaleEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

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

            // Keyed by SKU: inventory deducts stock per SKU and insight aggregates velocity per
            // SKU, so both consumers need the events for one SKU ordered on one partition.
            Message<ItemSoldEvent> message = MessageBuilder.withPayload(payload)
                    .setHeader("sku", payload.sku())
                    .setHeader("eventId", payload.eventId().toString())
                    .build();

            boolean sent = streamBridge.send(BINDING, message);
            if (sent) {
                log.info("[published] topic={} eventId={} occurredAt={} transactionId={} sku={} quantity={}",
                        ItemSoldEvent.TOPIC, payload.eventId(), payload.occurredAt(),
                        payload.transactionId(), payload.sku(), payload.quantity());
            } else {
                log.error("[publish-failed] topic={} eventId={} transactionId={} sku={}",
                        ItemSoldEvent.TOPIC, payload.eventId(), payload.transactionId(),
                        payload.sku());
            }
        }
    }
}
