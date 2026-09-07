package com.freshflow.inventory.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.inventory.application.ApplyItemSoldCommand;
import com.freshflow.inventory.application.ApplyItemSoldHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * FR2's inbound adapter &mdash; turns an {@code item-sold} integration event into the application
 * layer's command, exactly once.
 *
 * <p>Deliberately separate from {@link InventoryMessagingConfiguration}, which holds the Kafka
 * binding and no logic. This class holds the logic and imports no Kafka type, so the thing FR2
 * calls an acceptance criterion &mdash; "idempotent with respect to {@code eventId}" &mdash; can
 * be exercised without a broker. Same split as {@code sales-service}'s Week 6 adapter.
 *
 * <p><b>Why idempotency lives here and not in the application layer.</b> Deduplicating a
 * redelivered message is a property of the delivery, not of the use case. "Apply this sale to
 * stock" means the same thing however it arrived; that Kafka may deliver the same message twice
 * is a fact about Kafka. Putting the check here also keeps {@code eventId} out of the
 * {@code domain} package entirely, which matters because spec-01 &sect;2.3 declares this service's
 * ports in {@code domain}: a {@code ProcessedEventLog} port there would put messaging vocabulary
 * into a layer that is supposed to know nothing about messaging.
 *
 * <p><b>Why the record and the deduction share one transaction.</b> {@link Transactional} spans
 * both the insert of the consumed {@code eventId} and the deduction that
 * {@link ApplyItemSoldHandler} performs. If they could commit separately, a crash between them
 * would either deduct twice on redelivery or record an event whose deduction never happened. At
 * least-once delivery makes both reachable, so they commit together or not at all.
 */
@Component
public class ItemSoldMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ItemSoldMessageHandler.class);

    private final ConsumedEventJpaRepository consumedEvents;
    private final ApplyItemSoldHandler applyItemSoldHandler;

    public ItemSoldMessageHandler(ConsumedEventJpaRepository consumedEvents,
                                  ApplyItemSoldHandler applyItemSoldHandler) {
        this.consumedEvents = consumedEvents;
        this.applyItemSoldHandler = applyItemSoldHandler;
    }

    /**
     * Consumes one {@code item-sold} event. A redelivery of an {@code eventId} already applied is
     * discarded without touching stock.
     *
     * @return true if the sale was applied, false if it had already been applied
     */
    @Transactional
    public boolean handle(ItemSoldEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("[ignored] topic={} malformed event: {}", ItemSoldEvent.TOPIC, event);
            return false;
        }

        String eventId = event.eventId().toString();
        if (consumedEvents.existsById(eventId)) {
            log.info("[duplicate] topic={} eventId={} sku={} already applied; stock not deducted "
                            + "again", ItemSoldEvent.TOPIC, eventId, event.sku());
            return false;
        }

        // Recorded first and in the same transaction as the deduction below, so the two commit
        // together. Either the sale is applied and remembered, or neither happened.
        consumedEvents.save(new ConsumedEventJpaEntity(eventId, ItemSoldEvent.TOPIC, Instant.now()));

        applyItemSoldHandler.handle(new ApplyItemSoldCommand(
                event.sku(), event.quantity(), event.occurredAt()));

        return true;
    }
}
