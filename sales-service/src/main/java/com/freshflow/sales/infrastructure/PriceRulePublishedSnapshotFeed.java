package com.freshflow.sales.infrastructure;

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
 * Week 6 adapter for the {@code PriceSnapshotFeed} inbound port (spec-01 &sect;1.3, ADR 0001).
 *
 * <p>It materialises {@code sales-service}'s local {@code PriceSnapshot} from the
 * {@code price-rule-published} topic, replacing the Week 5 static-configuration adapter behind the
 * <b>same unchanged port signature</b>. spec-01 &sect;1.3 calls that "a Ports and Adapters
 * substitution, not an interim workaround", and this is the week that claim gets tested: no
 * application-layer code changed. {@code TransactionApplicationService} still asks
 * {@code priceSnapshotFeed.findBySku(sku)} and still cannot tell where the answer came from.
 *
 * <p>This class deliberately contains no Kafka or Spring Cloud Stream type. It is a plain
 * in-memory read model plus the two consumption rules spec-01 &sect;2.6 mandates for this topic.
 * The transport &mdash; the actual binding &mdash; lives in {@link SalesMessagingConfiguration},
 * which feeds events in through {@link #onPriceRulePublished}. Keeping the two apart means the
 * consumption rules can be exercised without a broker.
 *
 * <p><b>The two rules, per spec-01 &sect;2.6:</b>
 * <ol>
 *   <li><b>Deduplicate on {@code eventId}.</b> Re-delivery is normal in Kafka &mdash; at-least-once
 *       delivery, consumer rebalances, and this binding's replay-from-earliest configuration all
 *       produce it &mdash; so a repeated {@code eventId} is discarded rather than reapplied.</li>
 *   <li><b>Last-write-wins on {@code occurredAt} per {@code sku}.</b> Ordering is decided by
 *       producer-assigned event time, never by arrival order or broker ingestion time. An event
 *       that arrives late but happened earlier does not overwrite a newer price.</li>
 * </ol>
 *
 * <p>The seen-event set grows without bound. That is acceptable at this milestone &mdash; one
 * price rule per SKU, republished on producer start &mdash; and is recorded as a known limitation
 * in the Week 6 log rather than solved with a cache dependency nobody has approved.
 */
@Component
public class PriceRulePublishedSnapshotFeed implements PriceSnapshotFeed {

    private static final Logger log = LoggerFactory.getLogger(PriceRulePublishedSnapshotFeed.class);

    /** spec-01 §2.6 rule 1: idempotent consumption. */
    private final Set<UUID> consumedEventIds = ConcurrentHashMap.newKeySet();

    /** spec-01 §2.6 rule 2: one entry per SKU, the one with the latest {@code occurredAt}. */
    private final Map<String, TimestampedSnapshot> snapshotsBySku = new ConcurrentHashMap<>();

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
        } else {
            log.info("[stale] topic={} eventId={} occurredAt={} sku={} is older than the snapshot "
                            + "in force ({}); ignored",
                    PriceRulePublishedEvent.TOPIC, event.eventId(), event.occurredAt(), event.sku(),
                    winner.occurredAt());
        }
    }

    @Override
    public Optional<PriceSnapshot> findBySku(Sku sku) {
        return Optional.ofNullable(snapshotsBySku.get(sku.value()))
                .map(TimestampedSnapshot::snapshot);
    }

    /**
     * spec-03 &sect;2: where no markdown is in force the effective price equals the base price.
     * {@code price-rule-published} carries no markdown &mdash; that is what {@code markdown-applied}
     * and {@code markdown-expired} are for, and those are Week 7.
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
}
