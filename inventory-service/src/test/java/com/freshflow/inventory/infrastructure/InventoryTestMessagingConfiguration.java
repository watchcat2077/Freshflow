package com.freshflow.inventory.infrastructure;

import com.freshflow.inventory.domain.BatchReceived;
import com.freshflow.inventory.domain.DomainEventPublisher;
import com.freshflow.inventory.domain.StockLevelChanged;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-scope adapter for the {@code DomainEventPublisher} outbound port.
 *
 * <p>Under the {@code test} profile {@code freshflow.messaging.kafka.enabled} is false, so
 * {@link KafkaDomainEventPublisher} is not bound and the spec-04 MockMvc tests need no broker.
 * This supplies the port instead.
 *
 * <p>That substitution is not a testing trick; it is the same Ports and Adapters property the
 * Week 5 &rarr; Week 6 swap relies on (spec-01 &sect;1.3). The application layer is wired to the
 * port and cannot tell which adapter is behind it &mdash; a logging one, a Kafka one, or this.
 *
 * <p>It records rather than discards, so a test that wants to assert publication can.
 */
@Configuration
@Profile("test")
public class InventoryTestMessagingConfiguration {

    @Bean
    public RecordingDomainEventPublisher domainEventPublisher() {
        return new RecordingDomainEventPublisher();
    }

    /** Keeps every published event in order, for assertions. */
    public static class RecordingDomainEventPublisher implements DomainEventPublisher {

        private final List<BatchReceived> published = new CopyOnWriteArrayList<>();
        private final List<StockLevelChanged> stockLevelChanges = new CopyOnWriteArrayList<>();

        @Override
        public void publish(BatchReceived event) {
            published.add(event);
        }

        @Override
        public void publish(StockLevelChanged event) {
            stockLevelChanges.add(event);
        }

        public List<BatchReceived> published() {
            return List.copyOf(published);
        }

        /** FR2 -- one entry per batch actually touched by a sale, in the order they were touched. */
        public List<StockLevelChanged> stockLevelChanges() {
            return List.copyOf(stockLevelChanges);
        }

        public void clear() {
            published.clear();
            stockLevelChanges.clear();
        }
    }
}
