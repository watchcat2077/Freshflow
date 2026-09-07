package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.PriceRulePublishedEvent;
import com.freshflow.sales.application.SaleEventPublisher;
import com.freshflow.sales.domain.SaleTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-scope messaging wiring for {@code sales-service}.
 *
 * <p>Under the {@code test} profile {@code freshflow.messaging.kafka.enabled} is false, so neither
 * {@link KafkaSaleEventPublisher} nor {@link SalesMessagingConfiguration} is bound and the spec-04
 * MockMvc tests need no broker. Two things are supplied here in their place.
 *
 * <p><b>1. The {@code SaleEventPublisher} port</b> &mdash; a recording adapter, so that
 * {@code POST /api/transactions} completes exactly as it does in production, with the transaction
 * script unable to tell the difference.
 *
 * <p><b>2. The price data the tests read.</b> This replaces
 * {@code freshflow.sales.price-snapshot-seed}, which spec-01 &sect;2.7 retires this week. Note
 * carefully <i>how</i> it is supplied: by handing a real {@link PriceRulePublishedEvent} to the
 * real Week 6 adapter, {@link PriceRulePublishedSnapshotFeed}, through the same ingest method the
 * Kafka binding calls. The tests therefore exercise the production adapter &mdash; its event
 * translation, its deduplication and its last-write-wins rule &mdash; and only the transport is
 * stubbed. That is a stronger arrangement than the Week 5 one, which read a YAML block.
 *
 * <p>The values match the Week 5 seed exactly (spec-01 &sect;2.7), so the assertions in
 * {@code TransactionApiIntegrationTest} are unchanged: SKU-YOG-1KG, base price 4.50 AUD, unit cost
 * 2.10 AUD, category DAIRY.
 */
@Configuration
@Profile("test")
public class SalesTestMessagingConfiguration {

    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    @Bean
    public RecordingSaleEventPublisher saleEventPublisher() {
        return new RecordingSaleEventPublisher();
    }

    /**
     * Feeds the seeded price rule into the local snapshot through the port adapter's own ingest
     * path, before any test runs.
     *
     * <p>Returns a marker object only so that this is a bean and therefore instantiated during
     * context refresh; the work is the call, not the value.
     */
    @Bean
    public PriceSnapshotTestSeed priceSnapshotTestSeed(PriceRulePublishedSnapshotFeed snapshotFeed) {
        snapshotFeed.onPriceRulePublished(new PriceRulePublishedEvent(
                UUID.randomUUID(),
                Instant.now(),
                SEEDED_SKU,
                new MoneyPayload(new BigDecimal("4.50"), "AUD"),
                new MoneyPayload(new BigDecimal("2.10"), "AUD"),
                "DAIRY"));
        return new PriceSnapshotTestSeed();
    }

    /** Marker for the seeding bean above. */
    public static class PriceSnapshotTestSeed {
    }

    /** Keeps every published transaction in order, for assertions. */
    public static class RecordingSaleEventPublisher implements SaleEventPublisher {

        private final List<SaleTransaction> published = new CopyOnWriteArrayList<>();

        @Override
        public void publishItemSold(SaleTransaction transaction) {
            published.add(transaction);
        }

        public List<SaleTransaction> published() {
            return List.copyOf(published);
        }
    }
}
