package com.freshflow.inventory.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * Spring Cloud Stream bindings for {@code inventory-service} (spec-01 &sect;1.1).
 *
 * <p>One binding: the consumer side of {@code item-sold} (spec-01 &sect;2.6, Table A), which makes
 * Inventory the downstream half of the Customer&ndash;Supplier relationship with Sales that
 * &sect;1.5 describes. Sales publishes; Inventory deducts.
 *
 * <p>The transport lives here and the logic lives in {@link ItemSoldMessageHandler}, for the same
 * reason as in {@code sales-service}: it lets FR2's idempotency and FEFO behaviour be tested
 * without a broker.
 *
 * <p>Conditional on {@code freshflow.messaging.kafka.enabled}, which defaults to true; only the
 * integration-test profile switches it off.
 */
@Configuration
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class InventoryMessagingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InventoryMessagingConfiguration.class);

    /**
     * Subscribes to {@code item-sold} and deducts stock (FR2).
     *
     * <p>The binding name Spring Cloud Stream derives from this bean is {@code itemSold-in-0}, and
     * a durable consumer group is configured for it. That group is not decoration: unlike
     * {@code sales-service}'s price snapshot, which is a materialised view that any instance may
     * rebuild from the whole topic, a stock deduction must be applied by exactly one consumer and
     * must not be replayed from the beginning on every restart.
     */
    @Bean
    public Consumer<Message<ItemSoldEvent>> itemSold(ItemSoldMessageHandler messageHandler) {
        return message -> {
            ItemSoldEvent event = message.getPayload();
            log.debug("[received] topic={} eventId={} sku={} quantity={}",
                    ItemSoldEvent.TOPIC, event.eventId(), event.sku(), event.quantity());
            messageHandler.handle(event);
        };
    }
}
