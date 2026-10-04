package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.MarkdownAppliedEvent;
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
 *
 * <p><b>FR6b, 4 October 2026.</b> The same adapter now consumes {@code markdown-applied} and holds
 * the markdown it carries beside the base snapshot. The second half of this class states what that
 * is held to: the same deduplication, last-write-wins by event time among a SKU's markdowns, the
 * promotion window answered at scan time with no {@code markdown-expired} (B-7), and one documented
 * choice about a price rule that is newer than the markdown &mdash; it displaces it. The two topics
 * arrive on separate bindings, so the outcome must not depend on which arrives first.
 */
@DisplayName("spec-01 §2.6: price-rule-published and markdown-applied consumption rules")
class PriceRulePublishedSnapshotFeedTest {

    private static final String SKU = "SKU-YOG-1KG";
    private static final Instant NOON = Instant.parse("2026-09-07T12:00:00Z");

    /** FR6b: the promotion window the markdown tests attach &mdash; one o'clock until nine. */
    private static final Instant WINDOW_OPENS = NOON.plus(1, ChronoUnit.HOURS);
    private static final Instant WINDOW_CLOSES = NOON.plus(9, ChronoUnit.HOURS);

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

    /** A PERCENT_30 markdown on the SKU, over the window above unless told otherwise. */
    private MarkdownAppliedEvent markdown(UUID eventId, Instant occurredAt, String discountedPrice) {
        return markdown(eventId, occurredAt, discountedPrice, WINDOW_OPENS, WINDOW_CLOSES);
    }

    private MarkdownAppliedEvent markdown(UUID eventId, Instant occurredAt, String discountedPrice,
                                          Instant effectiveFrom, Instant effectiveTo) {
        return new MarkdownAppliedEvent(
                eventId,
                occurredAt,
                "MDP-0001",
                "BAT-0001",
                SKU,
                DiscountTier.PERCENT_30,
                effectiveFrom,
                effectiveTo,
                new MoneyPayload(new BigDecimal(discountedPrice), "AUD"));
    }

    private PriceSnapshot snapshotInForce() {
        Optional<PriceSnapshot> snapshot = feed.findBySku(Sku.of(SKU));
        assertThat(snapshot).as("snapshot for " + SKU).isPresent();
        return snapshot.orElseThrow();
    }

    private BigDecimal basePriceInForce() {
        return snapshotInForce().basePrice().amount();
    }

    // ---------------------------------------------------------- price-rule-published

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

    // --------------------------------------------------------- FR6b: markdown-applied

    /**
     * FR6b: "Consumes {@code markdown-applied} and maintains the markdown in the local price
     * snapshot." The discounted price is taken from the event, never recomputed from the tier:
     * the contract's javadoc says why &mdash; the price the till charges is the one Pricing
     * authorised, to the cent, and recomputing it here would duplicate a Pricing rule outside
     * Pricing.
     */
    @Test
    @DisplayName("a consumed markdown-applied attaches the markdown to the SKU's snapshot")
    void markdownAppliedAttachesToTheSnapshot() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));

        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));

        PriceSnapshot snapshot = snapshotInForce();
        assertThat(snapshot.basePrice().amount())
                .as("the base price is untouched by a markdown")
                .isEqualByComparingTo("4.50");
        assertThat(snapshot.effectivePrice().amount()).isEqualByComparingTo("3.15");
        assertThat(snapshot.discountTier()).isEqualTo("PERCENT_30");
        assertThat(snapshot.effectiveFrom()).isEqualTo(WINDOW_OPENS);
        assertThat(snapshot.effectiveTo()).isEqualTo(WINDOW_CLOSES);

        // spec-03 s2's rule, as the transaction script asks it: inside the window the discounted
        // price is in force and the line will say a markdown was applied.
        PriceSnapshot.PriceInForce inForce = snapshot.priceAt(NOON.plus(2, ChronoUnit.HOURS));
        assertThat(inForce.unitPrice().amount()).isEqualByComparingTo("3.15");
        assertThat(inForce.markdownApplied()).isTrue();
    }

    /**
     * Rule 1, for the second topic. The redelivery carries a deeper discount, so a
     * re-application would be visible as a cheaper price.
     */
    @Test
    @DisplayName("a redelivered markdown-applied eventId is ignored, even when its payload differs")
    void duplicateMarkdownEventIdIsIgnored() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        UUID eventId = UUID.randomUUID();
        feed.onMarkdownApplied(markdown(eventId, WINDOW_OPENS, "3.15"));

        feed.onMarkdownApplied(markdown(eventId, WINDOW_OPENS.plus(1, ChronoUnit.HOURS), "0.99"));

        assertThat(snapshotInForce().effectivePrice().amount())
                .as("the redelivery must not have been applied")
                .isEqualByComparingTo("3.15");
    }

    /**
     * FR6b: "A markdown is in force only inside its promotion window; a scan after
     * {@code effectiveTo} reverts to the base price without any further event." No
     * {@code markdown-expired} is consumed (B-7, ruled 4 October 2026): the snapshot keeps the
     * window and answers from it for whatever instant it is asked about. The window is half-open,
     * so its closing instant is already outside it and its opening instant is inside.
     */
    @Test
    @DisplayName("outside the promotion window the base price is in force, with no further event")
    void expiredMarkdownRevertsToBasePriceAtScanTime() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));
        PriceSnapshot snapshot = snapshotInForce();

        PriceSnapshot.PriceInForce afterClose = snapshot.priceAt(WINDOW_CLOSES.plus(1, ChronoUnit.HOURS));
        assertThat(afterClose.unitPrice().amount())
                .as("after effectiveTo the base price is back, with nothing having told the feed")
                .isEqualByComparingTo("4.50");
        assertThat(afterClose.markdownApplied()).isFalse();

        assertThat(snapshot.priceAt(WINDOW_CLOSES).markdownApplied())
                .as("the window is half-open: it has closed at effectiveTo itself")
                .isFalse();
        assertThat(snapshot.priceAt(WINDOW_OPENS.minus(1, ChronoUnit.MINUTES)).markdownApplied())
                .as("not yet in force before effectiveFrom")
                .isFalse();
        assertThat(snapshot.priceAt(WINDOW_OPENS).markdownApplied())
                .as("in force from effectiveFrom inclusive")
                .isTrue();
    }

    /**
     * The documented choice for a price rule that arrives after a markdown: a rule <b>newer</b>
     * than the markdown displaces it. The discounted price on {@code markdown-applied} was
     * authorised by Pricing against the base price in force at the time. Once Pricing publishes a
     * newer rule, that authorisation no longer describes the rule in force, and the till charges
     * the base price until Pricing authorises a markdown against the new one.
     */
    @Test
    @DisplayName("a price rule newer than the markdown displaces it")
    void laterPriceRuleDisplacesTheMarkdown() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));

        feed.onPriceRulePublished(event(UUID.randomUUID(), WINDOW_OPENS.plus(30, ChronoUnit.MINUTES), "5.20"));

        PriceSnapshot snapshot = snapshotInForce();
        assertThat(snapshot.basePrice().amount()).isEqualByComparingTo("5.20");
        assertThat(snapshot.discountTier())
                .as("the markdown was authorised against the old rule, so it goes with it")
                .isNull();
        PriceSnapshot.PriceInForce insideOldWindow = snapshot.priceAt(NOON.plus(2, ChronoUnit.HOURS));
        assertThat(insideOldWindow.unitPrice().amount()).isEqualByComparingTo("5.20");
        assertThat(insideOldWindow.markdownApplied()).isFalse();
    }

    /**
     * The other half of the same choice: a rule <b>older</b> than the markdown leaves it attached,
     * even when that rule is new enough to replace the base snapshot. The markdown was authorised
     * against a rule at least as new as this one.
     */
    @Test
    @DisplayName("a price rule older than the markdown replaces the base price and keeps the markdown")
    void olderPriceRuleKeepsTheMarkdown() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));

        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON.plus(30, ChronoUnit.MINUTES), "4.80"));

        PriceSnapshot snapshot = snapshotInForce();
        assertThat(snapshot.basePrice().amount())
                .as("newer than the base in force, so it wins rule 2")
                .isEqualByComparingTo("4.80");
        assertThat(snapshot.discountTier())
                .as("older than the markdown, so the markdown stays")
                .isEqualTo("PERCENT_30");
        assertThat(snapshot.priceAt(NOON.plus(2, ChronoUnit.HOURS)).unitPrice().amount())
                .isEqualByComparingTo("3.15");
    }

    /**
     * spec-01 &sect;2.6: ordering is decided by event time, never by arrival order. The two topics
     * are replayed on separate bindings at startup, so either may arrive first, and the same two
     * events must leave the same snapshot whichever way round they come. This is the case the
     * previous two tests would miss if the displacement rule were applied only when the rule is
     * the one arriving.
     */
    @Test
    @DisplayName("the outcome does not depend on which topic's event arrives first")
    void outcomeIsIndependentOfArrivalOrder() {
        // A rule newer than the markdown, with the markdown arriving second: still displaced.
        feed.onPriceRulePublished(event(UUID.randomUUID(), WINDOW_OPENS.plus(30, ChronoUnit.MINUTES), "5.20"));
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));

        assertThat(snapshotInForce().discountTier())
                .as("a markdown older than the rule in force is stale however it arrives")
                .isNull();

        // A markdown newer than its rule, arriving before any rule at all: held until the rule
        // arrives. Until then the SKU is absent -- FR6a's 400 -- because a markdown alone carries
        // no base price, unit cost or category, and selling at a discounted price with no rule in
        // force would be the silent-zero-price failure in a different coat.
        PriceRulePublishedSnapshotFeed fresh = new PriceRulePublishedSnapshotFeed();
        fresh.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));
        assertThat(fresh.findBySku(Sku.of(SKU)))
                .as("a markdown alone is not a price")
                .isEmpty();

        fresh.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        PriceSnapshot snapshot = fresh.findBySku(Sku.of(SKU)).orElseThrow();
        assertThat(snapshot.basePrice().amount()).isEqualByComparingTo("4.50");
        assertThat(snapshot.discountTier()).isEqualTo("PERCENT_30");
        assertThat(snapshot.effectivePrice().amount()).isEqualByComparingTo("3.15");
    }

    /**
     * Rule 2, for the second topic. Sales does not know batches (spec-03 &sect;0), and two batches
     * of one SKU may each be marked down; the markdown in force for the SKU is the one Pricing
     * authorised most recently, by event time rather than arrival.
     */
    @Test
    @DisplayName("the latest markdown by occurredAt is the one in force, whatever order they arrive in")
    void laterMarkdownWinsByEventTime() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS.plus(2, ChronoUnit.HOURS), "2.25"));

        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));

        assertThat(snapshotInForce().effectivePrice().amount())
                .as("the earlier markdown arriving late must not win on arrival order")
                .isEqualByComparingTo("2.25");
    }

    @Test
    @DisplayName("a malformed markdown-applied is dropped rather than corrupting the snapshot")
    void malformedMarkdownIsDropped() {
        feed.onPriceRulePublished(event(UUID.randomUUID(), NOON, "4.50"));
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS, "3.15"));

        // No eventId: nothing to deduplicate on.
        feed.onMarkdownApplied(new MarkdownAppliedEvent(
                null, WINDOW_OPENS.plus(1, ChronoUnit.HOURS), "MDP-0002", "BAT-0001", SKU,
                DiscountTier.PERCENT_60, WINDOW_OPENS, WINDOW_CLOSES,
                new MoneyPayload(new BigDecimal("1.80"), "AUD")));
        // A window that closes before it opens. Pricing's own invariant forbids it (spec-03 s3),
        // so it cannot be a real authorisation.
        feed.onMarkdownApplied(markdown(UUID.randomUUID(), WINDOW_OPENS.plus(1, ChronoUnit.HOURS),
                "1.80", WINDOW_CLOSES, WINDOW_OPENS));

        assertThat(snapshotInForce().effectivePrice().amount()).isEqualByComparingTo("3.15");
    }
}
