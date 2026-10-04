package com.freshflow.pricing.infrastructure;

import com.freshflow.contracts.MarkdownRecommendedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * Spring Cloud Stream bindings for {@code pricing-service} (spec-01 &sect;1.1).
 *
 * <p>One binding: the consumer side of {@code markdown-recommended} (spec-01 &sect;2.6, Table A),
 * the one topic that crosses the boundary this project is built around. FR14's agent publishes a
 * proposal and stops; whether it becomes a price is decided here, in this context's domain layer,
 * against the margin floor (FR11, spec-01 &sect;1.5). The recommendation is untrusted input to a
 * context that validates it, never a command.
 *
 * <p>The transport lives here and the logic lives in {@link MarkdownRecommendedMessageHandler}, for
 * the same reason as in the other two services: it lets the consumer's idempotency and its refusal
 * path be tested without a broker.
 *
 * <p>Conditional on {@code freshflow.messaging.kafka.enabled}, which defaults to true; only the
 * integration-test profile switches it off, and that profile also sets
 * {@code spring.cloud.function.definition} empty so nothing looks for this bean.
 */
@Configuration
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class PricingMessagingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PricingMessagingConfiguration.class);

    /**
     * Subscribes to {@code markdown-recommended} and puts each recommendation to the Pricing domain
     * (FR9 through the second entry point, FR11).
     *
     * <p>The binding name Spring Cloud Stream derives from this bean is
     * {@code markdownRecommended-in-0}, and a durable consumer group is configured for it: a
     * recorded plan is a change, not a view, so the topic must not be replayed from the beginning
     * on every restart. The handler's own idempotency guards against redelivery within the group.
     */
    @Bean
    public Consumer<Message<MarkdownRecommendedEvent>> markdownRecommended(
            MarkdownRecommendedMessageHandler messageHandler) {
        return message -> {
            MarkdownRecommendedEvent event = message.getPayload();
            log.debug("[received] topic={} eventId={} batchId={} sku={} tier={} agentTraceId={}",
                    MarkdownRecommendedEvent.TOPIC, event.eventId(), event.batchId(), event.sku(),
                    event.discountTier(), event.agentTraceId());
            messageHandler.handle(event);
        };
    }
}
