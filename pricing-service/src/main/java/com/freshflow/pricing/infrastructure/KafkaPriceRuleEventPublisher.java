package com.freshflow.pricing.infrastructure;

import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.PriceRulePublishedEvent;
import com.freshflow.pricing.domain.PriceRuleEventPublisher;
import com.freshflow.pricing.domain.PriceRulePublished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the {@code PriceRuleEventPublisher} outbound port &mdash; publishes
 * {@code price-rule-published} to Kafka through Spring Cloud Stream (spec-01 &sect;1.1, &sect;2.6).
 *
 * <p>spec-01 &sect;2.6's topic table names {@code pricing-service} as this topic's producer; this
 * class is that producer.
 *
 * <p>It translates the internal {@link PriceRulePublished} domain event into the
 * {@code contracts} module's {@link PriceRulePublishedEvent} &mdash; the Published Language of
 * spec-01 &sect;1.5. {@code sales-service} consumes that shared contract and never this context's
 * {@code Money} or {@code Sku}, which is exactly what makes Pricing &rarr; Sales a Published
 * Language relationship rather than a shared kernel (spec-03 &sect;6 rules out a shared kernel).
 *
 * <p>{@code eventId} is minted here, per publication, and is what the consumer deduplicates on.
 * {@code occurredAt} comes from the application layer's clock, so it is producer-assigned event
 * time and not broker ingestion time (spec-01 &sect;2.6).
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class KafkaPriceRuleEventPublisher implements PriceRuleEventPublisher {

    /**
     * Spring Cloud Stream binding name; its destination is configured in {@code application.yml}
     * as {@link PriceRulePublishedEvent#TOPIC}.
     */
    static final String BINDING = "priceRulePublished-out-0";

    private static final Logger log = LoggerFactory.getLogger(KafkaPriceRuleEventPublisher.class);

    private final StreamBridge streamBridge;

    public KafkaPriceRuleEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(PriceRulePublished event) {
        PriceRulePublishedEvent payload = new PriceRulePublishedEvent(
                UUID.randomUUID(),
                event.occurredAt(),
                event.sku().value(),
                new MoneyPayload(event.basePrice().amount(), event.basePrice().currency()),
                new MoneyPayload(event.unitCost().amount(), event.unitCost().currency()),
                event.category());

        // Keyed by SKU. The consuming side keeps one snapshot entry per SKU and resolves conflicts
        // by occurredAt; the key keeps a SKU's events on one partition so that ordering is not
        // left to chance in the first place.
        Message<PriceRulePublishedEvent> message = MessageBuilder.withPayload(payload)
                .setHeader("sku", payload.sku())
                .setHeader("eventId", payload.eventId().toString())
                .build();

        boolean sent = streamBridge.send(BINDING, message);
        if (sent) {
            log.info("[published] topic={} eventId={} occurredAt={} sku={} basePrice={} {} category={}",
                    PriceRulePublishedEvent.TOPIC, payload.eventId(), payload.occurredAt(),
                    payload.sku(), payload.basePrice().amount(), payload.basePrice().currency(),
                    payload.category());
        } else {
            log.error("[publish-failed] topic={} eventId={} sku={}",
                    PriceRulePublishedEvent.TOPIC, payload.eventId(), payload.sku());
        }
    }
}
