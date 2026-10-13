package com.freshflow.inventory.infrastructure;

import com.freshflow.contracts.BatchReceivedEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.StockLevelChangedEvent;
import com.freshflow.inventory.domain.BatchReceived;
import com.freshflow.inventory.domain.DomainEventPublisher;
import com.freshflow.inventory.domain.StockLevelChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the {@code DomainEventPublisher} outbound port &mdash; publishes
 * {@code batch-received} and {@code stock-level-changed} to Kafka through Spring Cloud Stream
 * (spec-01 &sect;1.1, &sect;2.6).
 *
 * <p>The port knows nothing of the transport: {@code BatchApplicationService} calls
 * {@code domainEventPublisher.publish(...)} and has no idea a broker exists. That
 * substitutability is the point of declaring the port in {@code domain} (spec-01 &sect;2.2).
 *
 * <p>Its architectural job is to translate the <i>internal</i> domain event into the
 * {@code contracts} module's record &mdash; the Published Language of spec-01 &sect;1.5 &mdash; so
 * this context's own value objects never cross a boundary.
 *
 * <p>Per spec-01 &sect;2.6, {@code eventId} is minted here because it identifies a publication and
 * is what a consumer deduplicates on, while {@code occurredAt} is carried through from the domain
 * because it is producer-assigned event time. Neither is ever taken from the broker.
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    /**
     * Spring Cloud Stream binding name. The topic it maps to is
     * {@code spring.cloud.stream.bindings.batchReceived-out-0.destination} in
     * {@code application.yml}, which is {@link BatchReceivedEvent#TOPIC}. The topic name is
     * configuration, not code.
     */
    static final String BINDING = "batchReceived-out-0";

    /** FR2's binding; its destination is {@link StockLevelChangedEvent#TOPIC}. */
    static final String STOCK_LEVEL_BINDING = "stockLevelChanged-out-0";

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

    private final StreamBridge streamBridge;

    public KafkaDomainEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(BatchReceived event) {
        BatchReceivedEvent payload = new BatchReceivedEvent(
                UUID.randomUUID(),
                event.occurredAt(),
                event.batchId().value(),
                event.sku().value(),
                event.quantity().value(),
                event.expiryAt().value(),
                new MoneyPayload(event.unitCost().amount(), event.unitCost().currency()));

        // Keyed by SKU so that all events for one SKU land on one partition and stay ordered.
        // insight-service's Kafka Streams topology aggregates by SKU and depends on that.
        Message<BatchReceivedEvent> message = MessageBuilder.withPayload(payload)
                .setHeader("sku", payload.sku())
                .setHeader("eventId", payload.eventId().toString())
                .build();

        boolean sent = streamBridge.send(BINDING, message);
        if (sent) {
            log.info("[published] topic={} eventId={} occurredAt={} batchId={} sku={} quantity={}",
                    BatchReceivedEvent.TOPIC, payload.eventId(), payload.occurredAt(),
                    payload.batchId(), payload.sku(), payload.quantity());
        } else {
            log.error("[publish-failed] topic={} eventId={} batchId={}",
                    BatchReceivedEvent.TOPIC, payload.eventId(), payload.batchId());
        }
    }

    /**
     * FR2 &mdash; announces what is left of a batch after a sale drew from it.
     *
     * <p>{@code occurredAt} and {@code changedAt} are both the selling event's own event time,
     * carried through from the domain event rather than read from a clock here. A sale that
     * reached this service late still changed stock at the moment it happened, and FR13 windows on
     * that instant (spec-01 &sect;2.6).
     */
    @Override
    public void publish(StockLevelChanged event) {
        StockLevelChangedEvent payload = new StockLevelChangedEvent(
                UUID.randomUUID(),
                event.changedAt(),
                event.batchId().value(),
                event.sku().value(),
                event.remainingQuantity().value(),
                event.changedAt());

        Message<StockLevelChangedEvent> message = MessageBuilder.withPayload(payload)
                .setHeader("sku", payload.sku())
                .setHeader("eventId", payload.eventId().toString())
                .build();

        boolean sent = streamBridge.send(STOCK_LEVEL_BINDING, message);
        if (sent) {
            log.info("[published] topic={} eventId={} occurredAt={} batchId={} sku={} remainingQuantity={}",
                    StockLevelChangedEvent.TOPIC, payload.eventId(), payload.occurredAt(),
                    payload.batchId(), payload.sku(), payload.remainingQuantity());
        } else {
            log.error("[publish-failed] topic={} eventId={} batchId={}",
                    StockLevelChangedEvent.TOPIC, payload.eventId(), payload.batchId());
        }
    }
}
