package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.MarkdownAppliedEvent;
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
 * <p>The two bindings here are the consumer sides of {@code price-rule-published} and, since
 * 4 October 2026, {@code markdown-applied} (spec-01 &sect;2.6; FR6b). Their destinations, their
 * replay-from-earliest settings and the broker address are all in {@code application.yml}; this
 * class only names the functions and hands each payload to the port adapter that owns the
 * consumption rules.
 *
 * <p>The transport is deliberately separated from {@link PriceRulePublishedSnapshotFeed}: the
 * adapter holds the deduplication, last-write-wins and markdown-composition logic and imports
 * nothing from Kafka, while this class holds the Kafka bindings and holds no logic. That split is
 * what lets the consumption rules be tested without a broker.
 *
 * <p>The binding names Spring Cloud Stream derives from the bean names are
 * {@code priceRulePublished-in-0} and {@code markdownApplied-in-0};
 * {@code spring.cloud.function.definition} lists both beans, separated by a semicolon.
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

    /**
     * Subscribes to {@code markdown-applied} and attaches the markdown it carries to the local
     * {@code PriceSnapshot} (FR6b, 4 October 2026).
     *
     * <p>Same shape as the price-rule binding and the same adapter behind it, because the markdown
     * is part of the same snapshot. The event's own {@code eventId} and {@code occurredAt} are used
     * for deduplication and ordering, never the broker's metadata (spec-01 &sect;2.6).
     */
    @Bean
    public Consumer<Message<MarkdownAppliedEvent>> markdownApplied(
            PriceRulePublishedSnapshotFeed snapshotFeed) {
        return message -> {
            MarkdownAppliedEvent event = message.getPayload();
            log.debug("[received] topic={} eventId={} sku={}", MarkdownAppliedEvent.TOPIC,
                    event.eventId(), event.sku());
            snapshotFeed.onMarkdownApplied(event);
        };
    }
}
