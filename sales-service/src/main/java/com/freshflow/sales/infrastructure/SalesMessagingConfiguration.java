package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.PriceRulePublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * Spring Cloud Stream bindings for {@code sales-service} (spec-01 &sect;1.1).
 *
 * <p>The one binding here is the consumer side of {@code price-rule-published} (spec-01
 * &sect;2.6). Its destination, its replay-from-earliest setting and its broker address are all in
 * {@code application.yml}; this class only names the function and hands each payload to the port
 * adapter that owns the consumption rules.
 *
 * <p>The transport is deliberately separated from {@link PriceRulePublishedSnapshotFeed}: the
 * adapter holds the deduplication and last-write-wins logic and imports nothing from Kafka, while
 * this class holds the Kafka binding and holds no logic. That split is what lets the consumption
 * rules be tested without a broker.
 *
 * <p>The binding name Spring Cloud Stream derives from the bean name is
 * {@code priceRulePublished-in-0}; {@code spring.cloud.function.definition} names the same bean.
 *
 * <p>The whole class is conditional on {@code freshflow.messaging.kafka.enabled}, which defaults to
 * true. Only the integration-test profiles switch it off, so that the MockMvc tests of spec-04 do
 * not require a running broker &mdash; see the Week 6 log.
 */
@Configuration
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class SalesMessagingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SalesMessagingConfiguration.class);

    /**
     * Subscribes to {@code price-rule-published} and feeds the local {@code PriceSnapshot}.
     *
     * <p>Takes {@link Message} rather than the bare payload so that the binding has access to the
     * Kafka headers when it needs them; the event's own {@code eventId} and {@code occurredAt} are
     * used for deduplication and ordering, never the broker's metadata (spec-01 &sect;2.6).
     */
    @Bean
    public Consumer<Message<PriceRulePublishedEvent>> priceRulePublished(
            PriceRulePublishedSnapshotFeed snapshotFeed) {
        return message -> {
            PriceRulePublishedEvent event = message.getPayload();
            log.debug("[received] topic={} eventId={} sku={}", PriceRulePublishedEvent.TOPIC,
                    event.eventId(), event.sku());
            snapshotFeed.onPriceRulePublished(event);
        };
    }
}
