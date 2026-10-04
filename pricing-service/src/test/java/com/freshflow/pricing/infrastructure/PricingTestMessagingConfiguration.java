package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.MarkdownApplied;
import com.freshflow.pricing.domain.MarkdownEventPublisher;
import com.freshflow.pricing.domain.PriceRuleEventPublisher;
import com.freshflow.pricing.domain.PriceRulePublished;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-scope adapters for this context's two outbound event ports, {@code PriceRuleEventPublisher}
 * and {@code MarkdownEventPublisher}.
 *
 * <p>Under the {@code test} profile {@code freshflow.messaging.kafka.enabled} is false, so
 * {@link KafkaPriceRuleEventPublisher} and {@link KafkaMarkdownEventPublisher} are not bound.
 * {@code PriceRuleSeeder} still announces the seeded rules on {@code ApplicationReadyEvent}, and a
 * markdown plan activated by {@code POST /api/markdown-plans} or by the {@code markdown-recommended}
 * consumer still publishes {@code markdown-applied}; both land here instead of on the broker.
 *
 * <p>They record rather than discard, which makes publication observable: after context startup
 * the price-rule list holds one {@link PriceRulePublished} per seeded SKU, and the markdown list
 * holds one {@link MarkdownApplied} per activation and none per rejection, which is the FR9/FR11
 * assertion the spec-04 tests make.
 */
@Configuration
@Profile("test")
public class PricingTestMessagingConfiguration {

    @Bean
    public RecordingPriceRuleEventPublisher priceRuleEventPublisher() {
        return new RecordingPriceRuleEventPublisher();
    }

    @Bean
    public RecordingMarkdownEventPublisher markdownEventPublisher() {
        return new RecordingMarkdownEventPublisher();
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

    /** FR9 -- one entry per activated plan, in the order they were activated. */
    public static class RecordingMarkdownEventPublisher implements MarkdownEventPublisher {

        private final List<MarkdownApplied> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(MarkdownApplied event) {
            published.add(event);
        }

        public List<MarkdownApplied> published() {
            return List.copyOf(published);
        }

        public void clear() {
            published.clear();
        }
    }
}
