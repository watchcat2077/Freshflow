package com.freshflow.sales.infrastructure;

import com.freshflow.contracts.MarkdownAppliedEvent;
import com.freshflow.contracts.PriceRulePublishedEvent;
import com.freshflow.sales.application.PriceSnapshotFeed;
import com.freshflow.sales.domain.Money;
import com.freshflow.sales.domain.PriceSnapshot;
import com.freshflow.sales.domain.Sku;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Week 6 adapter for the {@code PriceSnapshotFeed} inbound port (spec-01 &sect;1.3, ADR 0001),
 * extended on 4 October 2026 to consume {@code markdown-applied} as well (FR6b).
 *
 * <p>It materialises {@code sales-service}'s local {@code PriceSnapshot} from the
 * {@code price-rule-published} topic, replacing the Week 5 static-configuration adapter behind the
 * <b>same unchanged port signature</b>. spec-01 &sect;1.3 calls that "a Ports and Adapters
 * substitution, not an interim workaround", and this is the week that claim gets tested: no
 * application-layer code changed. {@code TransactionApplicationService} still asks
 * {@code priceSnapshotFeed.findBySku(sku)} and still cannot tell where the answer came from.
 *
 * <p>This class deliberately contains no Kafka or Spring Cloud Stream type. It is a plain
 * in-memory read model plus the consumption rules spec-01 &sect;2.6 mandates. The transport
 * &mdash; the actual bindings &mdash; lives in {@link SalesMessagingConfiguration}, which feeds
 * events in through {@link #onPriceRulePublished} and {@link #onMarkdownApplied}. Keeping the two
 * apart means the consumption rules can be exercised without a broker.
 *
 * <p><b>The two rules for {@code price-rule-published}, per spec-01 &sect;2.6:</b>
 * <ol>
 *   <li><b>Deduplicate on {@code eventId}.</b> Re-delivery is normal in Kafka &mdash; at-least-once
 *       delivery, consumer rebalances, and this binding's replay-from-earliest configuration all
 *       produce it &mdash; so a repeated {@code eventId} is discarded rather than reapplied.</li>
 *   <li><b>Last-write-wins on {@code occurredAt} per {@code sku}.</b> Ordering is decided by
 *       producer-assigned event time, never by arrival order or broker ingestion time. An event
 *       that arrives late but happened earlier does not overwrite a newer price.</li>
 * </ol>
 *
 * <p><b>FR6b &mdash; {@code markdown-applied}, 4 October 2026.</b> The same adapter consumes the
 * second topic, because the markdown is part of the same snapshot &mdash; spec-03 &sect;2 lists
 * {@code effectivePrice}, {@code discountTier}, {@code effectiveFrom} and {@code effectiveTo} among
 * its fields and names this topic as their source &mdash; and the port is "the only channel by
 * which price data enters {@code sales-service}". The class keeps its Week 6 name: every log line
 * and ai-log entry since then is anchored on it, and this javadoc says what it does. What the
 * second topic is held to:
 * <ol>
 *   <li><b>The same deduplication on {@code eventId}</b>, through the same seen-set. Event ids are
 *       UUIDs minted per publication, so one set serves both topics.</li>
 *   <li><b>Last-write-wins on {@code occurredAt} per {@code sku} among markdowns.</b> Sales does
 *       not know batches (spec-03 &sect;0), and two batches of one SKU may each be marked down; the
 *       markdown in force for the SKU is the one Pricing authorised most recently, by event time.</li>
 *   <li><b>The markdown is held apart from the base snapshot and composed with it on every
 *       read.</b> That is what makes the outcome a function of event times alone: the two topics
 *       are replayed on separate bindings at startup, either may arrive first, and nothing here
 *       depends on which did. A markdown for a SKU with no base snapshot yet is simply held, and
 *       the SKU stays absent &mdash; FR6a's 400 &mdash; until its rule arrives, because a markdown
 *       alone carries no base price, unit cost or category.</li>
 *   <li><b>A markdown applies only to a rule at least as old as itself.</b> The discounted price on
 *       {@code markdown-applied} was authorised by Pricing against the base price in force at the
 *       time &mdash; the margin floor is assessed against the SKU's {@code PriceRule} (spec-03
 *       &sect;3). A {@code price-rule-published} whose {@code occurredAt} is <i>after</i> the
 *       markdown's describes a different base price; the discounted price no longer corresponds to
 *       the rule in force and is not charged. Until Pricing authorises a markdown against the new
 *       rule, the till charges the base price, which is the conservative failure. A rule
 *       <i>older</i> than the markdown &mdash; a late or replayed one that still wins rule 2 for the
 *       base price &mdash; leaves the markdown attached. The comparison is strict, so a tie keeps
 *       the markdown, in the same spirit as rule 2's tie-break.</li>
 *   <li><b>No {@code markdown-expired} is consumed</b> (B-7, ruled 4 October 2026). The window
 *       travels on {@code markdown-applied} and stays on the snapshot; {@code PriceSnapshot#priceAt}
 *       answers for whatever instant it is asked about, so a late-arriving scan inside a window
 *       that has since closed is still priced at the discounted price (spec-03 &sect;2's rule). The
 *       feed never prunes by wall clock, because it does not know which instant it will be asked
 *       about next.</li>
 * </ol>
 *
 * <p>One consequence of rule 4 is worth stating here, because {@code pricing-service} re-announces
 * its seeded rules with a fresh {@code occurredAt} on every start: after a {@code pricing-service}
 * restart, every markdown this feed holds is displaced until {@code markdown-applied} is published
 * again. The base price is charged in the meantime, never a discount Pricing has not authorised
 * against the rule in force. Recorded in the 4 October 2026 log for the team.
 *
 * <p>The seen-event set grows without bound. That is acceptable at this milestone &mdash; one
 * price rule per SKU, republished on producer start, and one markdown per activated plan &mdash;
 * and is recorded as a known limitation in the Week 6 log rather than solved with a cache
 * dependency nobody has approved.
 */
@Component
public class PriceRulePublishedSnapshotFeed implements PriceSnapshotFeed {

    private static final Logger log = LoggerFactory.getLogger(PriceRulePublishedSnapshotFeed.class);

    /** spec-01 §2.6 rule 1: idempotent consumption, for both topics. */
    private final Set<UUID> consumedEventIds = ConcurrentHashMap.newKeySet();

    /** spec-01 §2.6 rule 2: one base snapshot per SKU, the one with the latest {@code occurredAt}. */
    private final Map<String, TimestampedSnapshot> snapshotsBySku = new ConcurrentHashMap<>();

    /**
     * FR6b: one markdown per SKU, the one with the latest {@code occurredAt}. Kept apart from the
     * base snapshot and composed with it in {@link #findBySku}.
     */
    private final Map<String, Markdown> markdownsBySku = new ConcurrentHashMap<>();

    /**
     * Applies one {@code price-rule-published} event to the local snapshot.
     *
     * <p>Package-visible to the binding that calls it, and to the tests that exercise the two
     * consumption rules directly.
     */
    void onPriceRulePublished(PriceRulePublishedEvent event) {
        if (event == null || event.eventId() == null || event.sku() == null
                || event.occurredAt() == null) {
            log.warn("[ignored] topic={} malformed event: {}", PriceRulePublishedEvent.TOPIC, event);
            return;
        }

        // Rule 1 -- deduplicate on eventId. add() returns false if it was already there.
        // Logged at INFO, not DEBUG: idempotent consumption is a specified behaviour of this
        // service, and a discarded redelivery is worth seeing. The volume is bounded by the
        // handful of standing price rules this topic carries.
        if (!consumedEventIds.add(event.eventId())) {
            log.info("[duplicate] topic={} eventId={} sku={} already consumed; ignored",
                    PriceRulePublishedEvent.TOPIC, event.eventId(), event.sku());
            return;
        }

        TimestampedSnapshot incoming = new TimestampedSnapshot(toSnapshot(event), event.occurredAt());

        // Rule 2 -- last-write-wins on occurredAt. Ties go to the later arrival, which is the
        // conventional reading of "last write wins" when event time cannot separate them.
        TimestampedSnapshot winner = snapshotsBySku.merge(event.sku(), incoming,
                (existing, candidate) ->
                        candidate.occurredAt().isBefore(existing.occurredAt()) ? existing : candidate);

        if (winner == incoming) {
            log.info("[consumed] topic={} eventId={} occurredAt={} sku={} basePrice={} {}",
                    PriceRulePublishedEvent.TOPIC, event.eventId(), event.occurredAt(), event.sku(),
                    event.basePrice().amount(), event.basePrice().currency());

            // FR6b rule 4, made visible. Nothing is removed: findBySku composes by event time, so
            // the markdown is already out of force the moment this rule is the one in force.
            Markdown markdown = markdownsBySku.get(event.sku());
            if (markdown != null && markdown.isOlderThan(event.occurredAt())) {
                log.info("[displaced] topic={} sku={} rule occurredAt={} is newer than the markdown "
                                + "authorised at {}; the base price is in force until Pricing "
                                + "authorises a markdown against this rule",
                        PriceRulePublishedEvent.TOPIC, event.sku(), event.occurredAt(),
                        markdown.occurredAt());
            }
        } else {
            log.info("[stale] topic={} eventId={} occurredAt={} sku={} is older than the snapshot "
                            + "in force ({}); ignored",
                    PriceRulePublishedEvent.TOPIC, event.eventId(), event.occurredAt(), event.sku(),
                    winner.occurredAt());
        }
    }

    /**
     * Applies one {@code markdown-applied} event to the local snapshot (FR6b).
     *
     * <p>Package-visible to the binding that calls it, and to the tests that exercise the
     * consumption rules directly.
     */
    void onMarkdownApplied(MarkdownAppliedEvent event) {
        if (event == null || event.eventId() == null || event.sku() == null
                || event.occurredAt() == null || event.discountedPrice() == null
                || event.discountTier() == null || event.effectiveFrom() == null
                || event.effectiveTo() == null
                // Pricing's own invariant: effectiveTo is after effectiveFrom (spec-03 s3). A
                // window that closes before it opens cannot be a real authorisation.
                || !event.effectiveTo().isAfter(event.effectiveFrom())) {
            log.warn("[ignored] topic={} malformed event: {}", MarkdownAppliedEvent.TOPIC, event);
            return;
        }

        // Rule 1 again, through the same seen-set.
        if (!consumedEventIds.add(event.eventId())) {
            log.info("[duplicate] topic={} eventId={} sku={} already consumed; ignored",
                    MarkdownAppliedEvent.TOPIC, event.eventId(), event.sku());
            return;
        }

        // The discounted price is carried, not recomputed from the tier: the contract says so, and
        // the reason is the Anti-Corruption Layer -- what a tier does to a price is Pricing's rule.
        Markdown incoming = new Markdown(
                Money.of(event.discountedPrice().amount(), event.discountedPrice().currency()),
                event.discountTier().name(),
                event.effectiveFrom(),
                event.effectiveTo(),
                event.occurredAt());

        // Rule 2 again: the latest markdown by occurredAt is the one in force for the SKU.
        Markdown winner = markdownsBySku.merge(event.sku(), incoming,
                (existing, candidate) ->
                        candidate.occurredAt().isBefore(existing.occurredAt()) ? existing : candidate);

        if (winner == incoming) {
            log.info("[consumed] topic={} eventId={} occurredAt={} sku={} tier={} discountedPrice={} {} "
                            + "window=[{}, {}) markdownPlanId={} batchId={}",
                    MarkdownAppliedEvent.TOPIC, event.eventId(), event.occurredAt(), event.sku(),
                    event.discountTier(), incoming.discountedPrice().amount(),
                    incoming.discountedPrice().currency(), event.effectiveFrom(), event.effectiveTo(),
                    event.markdownPlanId(), event.batchId());
        } else {
            log.info("[stale] topic={} eventId={} occurredAt={} sku={} is older than the markdown "
                            + "in force ({}); ignored",
                    MarkdownAppliedEvent.TOPIC, event.eventId(), event.occurredAt(), event.sku(),
                    winner.occurredAt());
        }
    }

    @Override
    public Optional<PriceSnapshot> findBySku(Sku sku) {
        TimestampedSnapshot base = snapshotsBySku.get(sku.value());
        if (base == null) {
            // FR6a: no rule, no price -- whether or not a markdown is waiting for one.
            return Optional.empty();
        }
        Markdown markdown = markdownsBySku.get(sku.value());
        if (markdown == null || markdown.isOlderThan(base.occurredAt())) {
            return Optional.of(base.snapshot());
        }
        return Optional.of(base.snapshot().withMarkdown(
                markdown.discountedPrice(), markdown.discountTier(),
                markdown.effectiveFrom(), markdown.effectiveTo()));
    }

    /**
     * spec-03 &sect;2: where no markdown is in force the effective price equals the base price.
     * {@code price-rule-published} carries no markdown &mdash; that is what {@code markdown-applied}
     * is for, and it is composed in at read time rather than here.
     */
    private PriceSnapshot toSnapshot(PriceRulePublishedEvent event) {
        return PriceSnapshot.withoutMarkdown(
                Sku.of(event.sku()),
                Money.of(event.basePrice().amount(), event.basePrice().currency()),
                Money.of(event.unitCost().amount(), event.unitCost().currency()),
                event.category());
    }

    /** A snapshot plus the event time that produced it, which is what last-write-wins compares. */
    private record TimestampedSnapshot(PriceSnapshot snapshot, Instant occurredAt) {
    }

    /**
     * What {@code markdown-applied} contributes to the snapshot, plus the event time Pricing
     * authorised it at, which is what both rule 2 and rule 4 compare.
     */
    private record Markdown(Money discountedPrice, String discountTier, Instant effectiveFrom,
                            Instant effectiveTo, Instant occurredAt) {

        /**
         * Rule 4: authorised against an older rule than the one that {@code ruleOccurredAt} dates.
         * Strict, so a markdown stamped at the same instant as its rule is kept.
         */
        boolean isOlderThan(Instant ruleOccurredAt) {
            return occurredAt.isBefore(ruleOccurredAt);
        }
    }
}
