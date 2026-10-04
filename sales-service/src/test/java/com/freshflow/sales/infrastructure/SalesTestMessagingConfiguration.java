package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.MarkdownAppliedEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.PriceRulePublishedEvent;
import com.freshflow.sales.application.SaleEventPublisher;
import com.freshflow.sales.domain.SaleTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-scope messaging wiring for {@code sales-service}.
 *
 * <p>Under the {@code test} profile {@code freshflow.messaging.kafka.enabled} is false, so neither
 * {@link KafkaSaleEventPublisher} nor {@link SalesMessagingConfiguration} is bound and the spec-04
 * MockMvc tests need no broker. Three things are supplied here in their place.
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
 *
 * <p><b>3. FR6b's markdown, since 4 October 2026.</b> A second SKU &mdash; {@code SKU-STR-250G},
 * spec-04's strawberries example: base price 6.00 AUD, unit cost 3.00 AUD, PRODUCE &mdash; is
 * seeded the same way and then given a PERCENT_30 markdown, discounted price 4.20 AUD, through a
 * real {@link MarkdownAppliedEvent} handed to the same adapter's second ingest method. Its
 * promotion window is deliberately in the past: it opened three hours before this class loaded
 * and closed one hour before, so a transaction stamped inside it is a late-arriving scan and one
 * stamped now is a scan after {@code effectiveTo}. The FR6b tests read the boundaries from
 * {@link #MARKDOWN_EFFECTIVE_FROM} and {@link #MARKDOWN_EFFECTIVE_TO} rather than guessing them.
 * The markdown is attached to a <i>different</i> SKU from the FR6a one so that every FR6a
 * assertion on SKU-YOG-1KG stands exactly as written.
 */
@Configuration
@Profile("test")
public class SalesTestMessagingConfiguration {

    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    /** FR6b: the SKU carrying a markdown. Base 6.00 AUD, marked down to 4.20 AUD. */
    public static final String MARKED_DOWN_SKU = "SKU-STR-250G";

    private static final Instant CONTEXT_LOADED = Instant.now();

    /** FR6b: the seeded markdown's window opened three hours ago ... */
    public static final Instant MARKDOWN_EFFECTIVE_FROM = CONTEXT_LOADED.minus(3, ChronoUnit.HOURS);

    /** ... and closed an hour ago, so a scan stamped "now" is after effectiveTo. */
    public static final Instant MARKDOWN_EFFECTIVE_TO = CONTEXT_LOADED.minus(1, ChronoUnit.HOURS);

    @Bean
    public RecordingSaleEventPublisher saleEventPublisher() {
        return new RecordingSaleEventPublisher();
    }

    /**
     * Feeds the seeded price rules, and FR6b's markdown, into the local snapshot through the port
     * adapter's own ingest paths, before any test runs.
     *
     * <p>Returns a marker object only so that this is a bean and therefore instantiated during
     * context refresh; the work is the calls, not the value.
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

        // FR6b. The rule is stamped before the markdown: a markdown is authorised against the rule
        // in force, and the adapter treats one older than the rule as stale. Both are then true of
        // this pair whichever binding would have delivered first.
        snapshotFeed.onPriceRulePublished(new PriceRulePublishedEvent(
                UUID.randomUUID(),
                MARKDOWN_EFFECTIVE_FROM.minus(1, ChronoUnit.HOURS),
                MARKED_DOWN_SKU,
                new MoneyPayload(new BigDecimal("6.00"), "AUD"),
                new MoneyPayload(new BigDecimal("3.00"), "AUD"),
                "PRODUCE"));
        snapshotFeed.onMarkdownApplied(new MarkdownAppliedEvent(
                UUID.randomUUID(),
                MARKDOWN_EFFECTIVE_FROM,
                "MDP-TEST-0001",
                "BAT-TEST-0001",
                MARKED_DOWN_SKU,
                DiscountTier.PERCENT_30,
                MARKDOWN_EFFECTIVE_FROM,
                MARKDOWN_EFFECTIVE_TO,
                new MoneyPayload(new BigDecimal("4.20"), "AUD")));
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
