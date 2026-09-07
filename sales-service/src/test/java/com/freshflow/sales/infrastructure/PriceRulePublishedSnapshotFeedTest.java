package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.PriceRulePublishedEvent;
import com.freshflow.sales.domain.PriceSnapshot;
import com.freshflow.sales.domain.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two consumption rules spec-01 &sect;2.6 states for {@code price-rule-published}:
 * "Consumers MUST deduplicate on {@code eventId} and MUST apply last-write-wins on
 * {@code occurredAt} per {@code sku}."
 *
 * <p>New in Week 6, and asserted here rather than end to end because both rules are about what
 * happens when delivery misbehaves &mdash; a redelivery, an out-of-order arrival &mdash; which a
 * healthy broker will not reliably produce on demand. No Spring context and no broker: the adapter
 * holds the rules and imports neither.
 */
@DisplayName("spec-01 §2.6: price-rule-published consumption rules")
class PriceRulePublishedSnapshotFeedTest {

    private static final String SKU = "SKU-YOG-1KG";
    private static final Instant NOON = Instant.parse("2026-09-07T12:00:00Z");

    private PriceRulePublishedSnapshotFeed feed;

    @BeforeEach
    void setUp() {
        feed = new PriceRulePublishedSnapshotFeed();
    }

    private PriceRulePublishedEvent event(UUID eventId, Instant occurredAt, String basePrice) {
        return new PriceRulePublishedEvent(
                eventId,
                occurredAt,
                SKU,
                new MoneyPayload(new BigDecimal(basePrice), "AUD"),
                new MoneyPayload(new BigDecimal("2.10"), "AUD"),
                "DAIRY");
    }

    private BigDecimal basePriceInForce() {
        Optional<PriceSnapshot> snapshot = feed.findBySku(Sku.of(SKU));
        assertThat(snapshot).as("snapshot for " + SKU).isPresent();
        return snapshot.orElseThrow().basePrice().amount();
    }

    @Test
    @DisplayName("a consumed event becomes the snapshot in force for its SKU")
    void firstEventPopulatesTheSnapshot() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));

        PriceSnapshot snapshot = feed.findBySku(Sku.of(SKU)).orElseThrow();
        assertThat(snapshot.basePrice().amount()).isEqualByComparingTo("4.50");
        assertThat(snapshot.unitCost().amount()).isEqualByComparingTo("2.10");
        assertThat(snapshot.category()).isEqualTo("DAIRY");
        // spec-03 s2: with no markdown in force, effectivePrice equals basePrice.
        assertThat(snapshot.effectivePrice()).isEqualTo(snapshot.basePrice());
    }

    @Test
    @DisplayName("a SKU never published is absent, so FR6a can answer 400 rather than a zero price")
    void unknownSkuIsAbsent() {
        assertThat(feed.findBySku(Sku.of("SKU-NEVER-PUBLISHED"))).isEmpty();
    }

    /**
     * Rule 1. The redelivery carries a different base price, so a re-application would be visible.
     * Kafka's at-least-once delivery makes this a real case, not a hypothetical one.
     */
    @Test
    @DisplayName("a redelivered eventId is ignored, even when its payload differs")
    void duplicateEventIdIsIgnored() {
        UUID eventId = UUID.randomUUID();
        feed.onPriceRulePublished(event(eventId, NOON, "4.50"));

        feed.onPriceRulePublished(event(eventId, NOON.plus(1, ChronoUnit.HOURS), "9.99"));

        assertThat(basePriceInForce())
                .as("the redelivery must not have been applied")
                .isEqualByComparingTo("4.50");
    }

    /** Rule 2, forwards: a genuinely newer event replaces the snapshot. */
    @Test
    @DisplayName("a later occurredAt replaces the snapshot in force")
    void laterEventWins() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));

        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON.plus(1, ChronoUnit.HOURS), "5.20"));

        assertThat(basePriceInForce()).isEqualByComparingTo("5.20");
    }

    /**
     * Rule 2, and the half that actually bites: ordering is decided by producer-assigned event
     * time, not by arrival order. An event that arrives second but happened first must not
     * overwrite the newer price. Deciding this by arrival would be relying on ingestion ordering,
     * which spec-01 &sect;2.6 forbids.
     */
    @Test
    @DisplayName("an earlier occurredAt arriving late does not overwrite a newer snapshot")
    void staleEventArrivingLateIsIgnored() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));

        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON.minus(6, ChronoUnit.HOURS), "3.00"));

        assertThat(basePriceInForce())
                .as("the stale event must not have won on arrival order")
                .isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("a malformed event is dropped rather than corrupting the snapshot")
    void malformedEventIsDropped() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));

        feed.onPriceRulePublished(new PriceRulePublishedEvent(
                null, NOON.plus(1, ChronoUnit.HOURS), SKU,
                new MoneyPayload(new BigDecimal("1.00"), "AUD"),
                new MoneyPayload(new BigDecimal("0.50"), "AUD"),
                "DAIRY"));

        assertThat(basePriceInForce()).isEqualByComparingTo("4.50");
    }
}
