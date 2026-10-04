package com.freshflow.pricing.infrastructure;

import com.freshflow.contracts.MarkdownAppliedEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.pricing.domain.MarkdownApplied;
import com.freshflow.pricing.domain.MarkdownEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the {@code MarkdownEventPublisher} outbound port &mdash; publishes
 * {@code markdown-applied} to Kafka through Spring Cloud Stream (spec-01 &sect;1.1, &sect;2.6 Table A).
 *
 * <p>It translates the internal {@link MarkdownApplied} domain event into the {@code contracts}
 * module's {@link MarkdownAppliedEvent} &mdash; the Published Language of spec-01 &sect;1.5.
 * {@code sales-service} consumes that contract for FR6b and never this context's {@code Money} or
 * {@code DiscountTier}; the tier crosses the boundary as the wire enum of the same four names.
 *
 * <p>{@code eventId} is minted here, per publication, and is what the consumer deduplicates on.
 * {@code occurredAt} comes from the application layer's clock, so it is producer-assigned event
 * time and not broker ingestion time (spec-01 &sect;2.6). Same shape as
 * {@link KafkaPriceRuleEventPublisher}, which is the pattern this service already runs.
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class KafkaMarkdownEventPublisher implements MarkdownEventPublisher {

    /**
     * Spring Cloud Stream binding name; its destination is configured in {@code application.yml}
     * as {@link MarkdownAppliedEvent#TOPIC}. The topic name is configuration, not code.
     */
    static final String BINDING = "markdownApplied-out-0";

    private static final Logger log = LoggerFactory.getLogger(KafkaMarkdownEventPublisher.class);

    private final StreamBridge streamBridge;

    public KafkaMarkdownEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(MarkdownApplied event) {
        MarkdownAppliedEvent payload = new MarkdownAppliedEvent(
                UUID.randomUUID(),
                event.occurredAt(),
                event.markdownPlanId().value(),
                event.batchId().value(),
                event.sku().value(),
                com.freshflow.contracts.DiscountTier.valueOf(event.discountTier().name()),
                event.window().effectiveFrom(),
                event.window().effectiveTo(),
                new MoneyPayload(event.discountedPrice().amount(), event.discountedPrice().currency()));

        // Keyed by SKU, like price-rule-published: the consuming side keeps one snapshot entry per
        // SKU, and the key keeps a SKU's markdowns on one partition so their order is not left to
        // chance.
        Message<MarkdownAppliedEvent> message = MessageBuilder.withPayload(payload)
                .setHeader("sku", payload.sku())
                .setHeader("eventId", payload.eventId().toString())
                .build();

        boolean sent = streamBridge.send(BINDING, message);
        if (sent) {
            log.info("[published] topic={} eventId={} occurredAt={} markdownPlanId={} batchId={} sku={} "
                            + "tier={} discountedPrice={} {} window={}..{}",
                    MarkdownAppliedEvent.TOPIC, payload.eventId(), payload.occurredAt(),
                    payload.markdownPlanId(), payload.batchId(), payload.sku(), payload.discountTier(),
                    payload.discountedPrice().amount(), payload.discountedPrice().currency(),
                    payload.effectiveFrom(), payload.effectiveTo());
        } else {
            log.error("[publish-failed] topic={} eventId={} markdownPlanId={} sku={}",
                    MarkdownAppliedEvent.TOPIC, payload.eventId(), payload.markdownPlanId(), payload.sku());
        }
    }
}
