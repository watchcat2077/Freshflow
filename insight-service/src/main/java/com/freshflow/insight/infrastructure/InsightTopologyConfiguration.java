package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.BatchReceivedEvent;
import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import com.freshflow.contracts.StockLevelChangedEvent;
import com.freshflow.insight.domain.BatchRiskView;
import com.freshflow.insight.domain.RiskAssessmentPolicy;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The Insight context's Kafka Streams topology &mdash; FR12's rolling velocity and FR13's expiry
 * risk detection, in one topology.
 *
 * <p><b>Why one function and not two.</b> FR13 is defined as a "stream-table join between the
 * velocity stream and a {@code KTable} of live batch state". The velocity stream is FR12's output,
 * and a {@code KStream} cannot cross from one Spring Cloud Stream function to another without
 * going back through a topic. Keeping both in one function keeps the join in memory, keeps every
 * state store under one {@code application.id} where Interactive Query can reach it, and avoids
 * inventing an internal topic purely to pass a stream between two halves of the same service.
 * The cost is the curried signature below, which is Spring Cloud Stream's idiom for more than two
 * inputs.
 *
 * <table>
 *   <caption>Bindings</caption>
 *   <tr><th>Binding</th><th>Topic</th><th>Role</th></tr>
 *   <tr><td>{@code insightTopology-in-0}</td><td>{@code item-sold}</td><td>FR12's event stream</td></tr>
 *   <tr><td>{@code insightTopology-in-1}</td><td>{@code velocity-baseline-published}</td><td>FR12's baseline KTable</td></tr>
 *   <tr><td>{@code insightTopology-in-2}</td><td>{@code batch-received}</td><td>FR13 batch state: expiry and opening quantity</td></tr>
 *   <tr><td>{@code insightTopology-in-3}</td><td>{@code stock-level-changed}</td><td>FR13 batch state: what remains</td></tr>
 *   <tr><td>{@code insightTopology-out-0}</td><td>{@code sell-through-risk-detected}</td><td>FR13's detection</td></tr>
 * </table>
 *
 * <p><b>Event time throughout.</b> All three input streams are timestamped by
 * {@link OccurredAtTimestampExtractor}, so windows are placed by producer-assigned
 * {@code occurredAt}. FR13's "now" is then the <i>end of the closed velocity window</i>, never a
 * clock: {@code hoursToExpiry}, {@code projectedSales} and {@code detectedAt} are all measured
 * from it. spec-01 &sect;2.6, and the reason the whole chain can be replayed and produce the same
 * answers.
 */
@Configuration
@EnableConfigurationProperties({VelocityProperties.class, RiskProperties.class})
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class InsightTopologyConfiguration {

    /** FR12's windowed aggregate: units sold per SKU per window. */
    public static final String WINDOW_STORE = "velocity-windows";

    /** FR12's baseline KTable. */
    public static final String BASELINE_STORE = "velocity-baselines";

    /** FR13's live batch state, keyed by SKU. */
    public static final String BATCH_STATE_STORE = "batch-states";

    /** FR13's assessments, keyed by batchId. Serves {@code GET /api/insight/risks}. */
    public static final String RISK_STORE = "batch-risks";

    private static final int SECONDS_PER_HOUR = 3600;

    private static final Logger log = LoggerFactory.getLogger(InsightTopologyConfiguration.class);

    private final VelocityProperties velocity;
    private final RiskProperties risk;
    private final RiskAssessmentPolicy riskAssessmentPolicy;

    public InsightTopologyConfiguration(VelocityProperties velocity, RiskProperties risk,
                                        RiskAssessmentPolicy riskAssessmentPolicy) {
        this.velocity = velocity;
        this.risk = risk;
        this.riskAssessmentPolicy = riskAssessmentPolicy;
    }

    /** The extractor every input binding names in {@code application.yml}. */
    @Bean
    public TimestampExtractor occurredAtTimestampExtractor() {
        return new OccurredAtTimestampExtractor();
    }

    @Bean
    public Function<KStream<String, ItemSoldEvent>,
            Function<KTable<String, VelocityBaselinePublishedEvent>,
            Function<KStream<String, BatchReceivedEvent>,
            Function<KStream<String, StockLevelChangedEvent>,
                    KStream<String, SellThroughRiskDetectedEvent>>>>> insightTopology() {

        return itemSold -> baselines -> batchReceived -> stockLevelChanged -> {
            log.info("FR12 topology: window={}s advancing every {}s, grace {}s, retention {}min; "
                            + "windowing on producer-assigned occurredAt (spec-01 s2.6)",
                    velocity.getWindowSeconds(), velocity.getAdvanceSeconds(),
                    velocity.getGraceSeconds(), velocity.getRetentionMinutes());
            log.info("FR13 topology: shortfall threshold {} unit(s), {} trading hours per day",
                    risk.getShortfallThreshold(), risk.getTradingHoursPerDay());

            // ---------------------------------------------------------------- FR12
            // "consumes item-sold, keys by SKU". Keyed explicitly rather than trusting the
            // producer's message key, so the aggregate is right for any producer on this topic.
            KTable<org.apache.kafka.streams.kstream.Windowed<String>, Long> windowed = itemSold
                    .filter((key, event) -> event != null && event.sku() != null)
                    .groupBy((key, event) -> event.sku(),
                            Grouped.with(Serdes.String(), new ItemSoldEventSerde()))
                    .windowedBy(TimeWindows.ofSizeAndGrace(velocity.window(), velocity.grace())
                            .advanceBy(velocity.advance()))
                    .aggregate(
                            () -> 0L,
                            (sku, event, total) -> total + event.quantity(),
                            Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(WINDOW_STORE)
                                    .withKeySerde(Serdes.String())
                                    .withValueSerde(Serdes.Long())
                                    .withRetention(velocity.retention()));

            // FR12's baseline table, materialised so Interactive Query can read it by name.
            baselines.filter((sku, baseline) -> baseline != null,
                    Materialized.<String, VelocityBaselinePublishedEvent, KeyValueStore<Bytes, byte[]>>
                                    as(BASELINE_STORE)
                            .withKeySerde(Serdes.String())
                            .withValueSerde(new VelocityBaselineSerde()));

            // ---------------------------------------------------------------- FR13
            // The KTable of live batch state, folded from the two topics that between them know
            // a batch's expiry and what is left of it. Keyed by SKU because the join partner is
            // a per-SKU velocity stream; see SkuBatchStates.
            JsonSerde<SkuBatchStates.BatchStateUpdate> updateSerde =
                    new JsonSerde<>(SkuBatchStates.BatchStateUpdate.class);
            JsonSerde<SkuBatchStates> statesSerde = new JsonSerde<>(SkuBatchStates.class);
            JsonSerde<VelocitySnapshot> velocitySerde = new JsonSerde<>(VelocitySnapshot.class);
            JsonSerde<RiskAssessment> assessmentSerde = new JsonSerde<>(RiskAssessment.class);

            KStream<String, SkuBatchStates.BatchStateUpdate> receivedUpdates = batchReceived
                    .filter((key, event) -> event != null && event.sku() != null)
                    .map((key, event) -> KeyValue.pair(event.sku(),
                            new SkuBatchStates.BatchStateUpdate(event.batchId(), event.sku(),
                                    event.expiryAt(), event.quantity(), event.occurredAt())));

            KStream<String, SkuBatchStates.BatchStateUpdate> stockUpdates = stockLevelChanged
                    .filter((key, event) -> event != null && event.sku() != null)
                    // expiryAt is null here on purpose: this topic does not carry it, and its
                    // absence is what tells SkuBatchStates this is a change and not a creation.
                    .map((key, event) -> KeyValue.pair(event.sku(),
                            new SkuBatchStates.BatchStateUpdate(event.batchId(), event.sku(),
                                    null, event.remainingQuantity(), event.occurredAt())));

            KTable<String, SkuBatchStates> batchStates = receivedUpdates
                    .merge(stockUpdates)
                    .groupByKey(Grouped.with(Serdes.String(), updateSerde))
                    .aggregate(
                            SkuBatchStates::empty,
                            (sku, update, states) -> states.apply(update),
                            Materialized.<String, SkuBatchStates, KeyValueStore<Bytes, byte[]>>
                                            as(BATCH_STATE_STORE)
                                    .withKeySerde(Serdes.String())
                                    .withValueSerde(statesSerde));

            // The velocity stream FR13 joins against: one final value per window, emitted when the
            // window closes in event time. Suppression matters here in a way it does not for
            // FR12's endpoint. Without it every sale would emit a dozen partial windows, each
            // under-reporting the rate, and each would trigger a fresh risk assessment on a figure
            // that was never true.
            KStream<String, VelocitySnapshot> velocityStream = windowed
                    .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                    .toStream()
                    .map((windowedKey, total) -> {
                        Instant windowStart = windowedKey.window().startTime();
                        Instant windowEnd = windowedKey.window().endTime();
                        double windowHours = (double) Duration.between(windowStart, windowEnd)
                                .toSeconds() / SECONDS_PER_HOUR;
                        double perHour = windowHours > 0 ? total / windowHours : 0.0;
                        return KeyValue.pair(windowedKey.key(), new VelocitySnapshot(
                                windowedKey.key(), windowStart, windowEnd, total, perHour));
                    });

            // FR13's stream-table join. One closed window per SKU assesses every batch of that SKU.
            KTable<String, RiskAssessment> assessments = velocityStream
                    .join(batchStates,
                            this::assessEveryBatch,
                            Joined.with(Serdes.String(), velocitySerde, statesSerde))
                    .flatMap((sku, views) -> {
                        List<KeyValue<String, RiskAssessment>> out = new ArrayList<>();
                        for (BatchRiskView view : views) {
                            out.add(KeyValue.pair(view.batchId(), RiskAssessment.initial(view)));
                        }
                        return out;
                    })
                    .groupByKey(Grouped.with(Serdes.String(), assessmentSerde))
                    // reduce, not aggregate, because what is needed is the PREVIOUS assessment for
                    // this batch -- which is exactly what a reducer is handed.
                    .reduce((previous, candidate) -> candidate.succeeding(previous),
                            Materialized.<String, RiskAssessment, KeyValueStore<Bytes, byte[]>>
                                            as(RISK_STORE)
                                    .withKeySerde(Serdes.String())
                                    .withValueSerde(assessmentSerde));

            // Only the transition into risk is published. The standing list of what is at risk is
            // the read model this same store already serves.
            return assessments.toStream()
                    .filter((batchId, assessment) -> assessment != null && assessment.newlyAtRisk())
                    .mapValues(this::toEvent);
        };
    }

    /**
     * The join function: one closed velocity window for a SKU, assessed against every batch of
     * that SKU currently on hand.
     */
    private List<BatchRiskView> assessEveryBatch(VelocitySnapshot snapshot, SkuBatchStates states) {
        List<BatchRiskView> views = new ArrayList<>();
        for (SkuBatchStates.BatchState batch : states.batches().values()) {
            views.add(riskAssessmentPolicy.assess(
                    batch.batchId(),
                    batch.sku(),
                    batch.remainingQuantity(),
                    batch.expiryAt(),
                    snapshot.velocityPerHour(),
                    // "Now" is the end of the closed window: event time, never a clock.
                    snapshot.windowEnd()));
        }
        return views;
    }

    private SellThroughRiskDetectedEvent toEvent(RiskAssessment assessment) {
        BatchRiskView view = assessment.view();
        SellThroughRiskDetectedEvent event = new SellThroughRiskDetectedEvent(
                UUID.randomUUID(),
                view.detectedAt(),
                view.batchId(),
                view.sku(),
                view.remainingQuantity(),
                view.hoursToExpiry(),
                view.currentVelocity(),
                view.projectedShortfall());

        log.info("[detected] topic={} eventId={} occurredAt={} batchId={} sku={} remaining={} "
                        + "hoursToExpiry={} velocity={}/h projectedShortfall={}",
                SellThroughRiskDetectedEvent.TOPIC, event.eventId(), event.occurredAt(),
                event.batchId(), event.sku(), event.remainingQuantity(), event.hoursToExpiry(),
                event.currentVelocity(), event.projectedShortfall());
        return event;
    }
}
