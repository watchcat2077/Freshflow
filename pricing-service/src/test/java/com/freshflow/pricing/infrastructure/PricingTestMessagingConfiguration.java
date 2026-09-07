package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.PriceRuleEventPublisher;
import com.freshflow.pricing.domain.PriceRulePublished;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-scope adapter for the {@code PriceRuleEventPublisher} outbound port.
 *
 * <p>Under the {@code test} profile {@code freshflow.messaging.kafka.enabled} is false, so
 * {@link KafkaPriceRuleEventPublisher} is not bound. {@code PriceRuleSeeder} still announces the
 * seeded rules on {@code ApplicationReadyEvent}, and those announcements land here instead of on
 * the broker.
 *
 * <p>It records them, which incidentally makes the announcement observable: after context startup
 * this list holds one {@link PriceRulePublished} per seeded SKU.
 */
@Configuration
@Profile("test")
public class PricingTestMessagingConfiguration {

    @Bean
    public RecordingPriceRuleEventPublisher priceRuleEventPublisher() {
        return new RecordingPriceRuleEventPublisher();
    }

    /** Keeps every published event in order, for assertions. */
    public static class RecordingPriceRuleEventPublisher implements PriceRuleEventPublisher {

        private final List<PriceRulePublished> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(PriceRulePublished event) {
            published.add(event);
        }

        public List<PriceRulePublished> published() {
            return List.copyOf(published);
        }
    }
}
