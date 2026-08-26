package com.freshflow.inventory.infrastructure;

import com.freshflow.contracts.BatchReceivedEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.inventory.domain.BatchReceived;
import com.freshflow.inventory.domain.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Week 5 adapter for the {@code DomainEventPublisher} outbound port.
 *
 * <p>The current milestone defines event publishing as "an outbound port interface in the domain
 * layer, with a logging adapter in infrastructure. No broker this week." This is that adapter. The
 * Kafka adapter is Week 6 and replaces this class behind the same unchanged port.
 *
 * <p>It does two jobs, and the second is the one that matters architecturally: it translates the
 * internal domain event into the {@code batch-received} integration event of the {@code contracts}
 * module &mdash; the Published Language (spec-01 &sect;1.5). Only that translated form would ever
 * cross a context boundary. {@code eventId} is minted here because it identifies a publication;
 * {@code occurredAt} is carried through from the domain because it is producer-assigned event time
 * and windowing depends on it (spec-01 &sect;2.6).
 */
@Component
public class LoggingDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

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

        log.info("[event] topic={} eventId={} occurredAt={} batchId={} sku={} quantity={} expiryAt={} unitCost={} {}",
                BatchReceivedEvent.TOPIC, payload.eventId(), payload.occurredAt(), payload.batchId(),
                payload.sku(), payload.quantity(), payload.expiryAt(),
                payload.unitCost().amount(), payload.unitCost().currency());
    }
}
