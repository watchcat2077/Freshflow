package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.BatchReceivedEvent;
import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import com.freshflow.contracts.StockLevelChangedEvent;
import com.freshflow.insight.domain.RiskAssessmentPolicy;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static com.freshflow.insight.infrastructure.InsightTopologyConfiguration.BASELINE_STORE;
import static com.freshflow.insight.infrastructure.InsightTopologyConfiguration.RISK_STORE;
import static com.freshflow.insight.infrastructure.InsightTopologyConfiguration.WINDOW_STORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The Kafka Streams topology of spec-05, driven by {@link TopologyTestDriver}: no broker, no Spring
 * context, no clock.
 *
 * <p><b>What is under test is the real topology.</b> The four inputs are built exactly as the
 * Kafka Streams binder builds them from {@code application.yml} &mdash; the project's own serdes,
 * and {@link OccurredAtTimestampExtractor} on the three bindings that name it &mdash; and handed
 * to {@link InsightTopologyConfiguration#insightTopology()}, the same curried function Spring
 * binds at runtime. The configuration object is constructed by hand with the values
 * {@code application.yml} carries, and the policy exactly as {@link RiskPolicyConfiguration}
 * constructs it. Nothing here is a restatement of the topology; a change to the topology is a
 * change to what these tests see.
 *
 * <p><b>Why there is no clock.</b> The topology has no {@code Clock} to fix because it never
 * consults one: windows are placed by the producer-assigned {@code occurredAt}, and FR13's "now" is
 * the end of the closed window (spec-01 &sect;2.6, spec-05 &sect;2.3 and &sect;3.2). Stream time is
 * therefore advanced here the only way it can be advanced in production &mdash; by a later event
 * &mdash; and the first test makes the point directly by piping records whose Kafka timestamps are
 * decades away from their {@code occurredAt}.
 *
 * <p><b>How a window closes.</b> {@code suppress(untilWindowCloses)} releases a window once stream
 * time passes its end plus the 5-second grace (spec-05 &sect;2.5). With 60-second windows
 * advancing every 5 seconds, a sale at {@code T0+10s} lands in twelve windows ending between
 * {@code T0+15s} and {@code T0+70s}; a second sale at {@code T0+70s} moves stream time to
 * {@code T0+70s} and closes the eleven of them that end by {@code T0+65s}. Each closed window
 * assesses every batch of the SKU, and only the first assessment of a batch that is at risk is the
 * transition FR13 publishes (spec-05 &sect;3.3). That is the arithmetic behind "exactly one event".
 *
 * <p>Instants are chosen on the 5-second hop grid so that every window edge in the assertions can
 * be read off by hand.
 */
@DisplayName("spec-05: the FR12/FR13 Kafka Streams topology under TopologyTestDriver")
class InsightTopologyTest {

    /** On the minute, so it is a window start and a hop boundary at once. */
    private static final Instant T0 = Instant.parse("2026-10-13T08:00:00Z");

    private static final String SKU = "SKU-YOG-1KG";
    private static final MoneyPayload UNIT_PRICE = new MoneyPayload(new BigDecimal("6.50"), "AUD");
    private static final MoneyPayload UNIT_COST = new MoneyPayload(new BigDecimal("2.10"), "AUD");

    /** Three hours after T0. With "now" inside the first minute, {@code hoursToExpiry} truncates to 2. */
    private static final Instant EXPIRY_AT = T0.plus(Duration.ofHours(3));

    @TempDir
    Path stateDir;

    private TopologyTestDriver driver;
    private TestInputTopic<String, ItemSoldEvent> itemSold;
    private TestInputTopic<String, VelocityBaselinePublishedEvent> baselines;
    private TestInputTopic<String, BatchReceivedEvent> batchReceived;
    private TestInputTopic<String, StockLevelChangedEvent> stockLevelChanged;
    private TestOutputTopic<String, SellThroughRiskDetectedEvent> riskDetected;

    @BeforeEach
    void buildTheRealTopology() {
        // freshflow.insight.velocity and freshflow.insight.risk, as application.yml sets them.
        VelocityProperties velocity = new VelocityProperties();
        velocity.setWindowSeconds(60);
        velocity.setAdvanceSeconds(5);
        velocity.setGraceSeconds(5);
        velocity.setRetentionMinutes(10);
        RiskProperties risk = new RiskProperties();
        risk.setShortfallThreshold(5.0);
        risk.setTradingHoursPerDay(24.0);

        // What RiskPolicyConfiguration#riskAssessmentPolicy does.
        RiskAssessmentPolicy policy = new RiskAssessmentPolicy(risk.getShortfallThreshold(),
                risk.getTradingHoursPerDay());
        InsightTopologyConfiguration configuration =
                new InsightTopologyConfiguration(velocity, risk, policy);

        // The four bindings, as the Kafka Streams binder builds them from application.yml:
        // in-0, in-2 and in-3 name occurredAtTimestampExtractor; in-1 feeds a KTable and does not.
        StreamsBuilder builder = new StreamsBuilder();
        TimestampExtractor eventTime = configuration.occurredAtTimestampExtractor();

        KStream<String, ItemSoldEvent> in0 = builder.stream(ItemSoldEvent.TOPIC,
                Consumed.with(Serdes.String(), new ItemSoldEventSerde())
                        .withTimestampExtractor(eventTime));
        KTable<String, VelocityBaselinePublishedEvent> in1 = builder.table(
                VelocityBaselinePublishedEvent.TOPIC,
                Consumed.with(Serdes.String(), new VelocityBaselineSerde()));
        KStream<String, BatchReceivedEvent> in2 = builder.stream(BatchReceivedEvent.TOPIC,
                Consumed.with(Serdes.String(), new BatchReceivedEventSerde())
                        .withTimestampExtractor(eventTime));
        KStream<String, StockLevelChangedEvent> in3 = builder.stream(StockLevelChangedEvent.TOPIC,
                Consumed.with(Serdes.String(), new StockLevelChangedEventSerde())
                        .withTimestampExtractor(eventTime));

        configuration.insightTopology().apply(in0).apply(in1).apply(in2).apply(in3)
                .to(SellThroughRiskDetectedEvent.TOPIC,
                        Produced.with(Serdes.String(), new SellThroughRiskDetectedEventSerde()));

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "freshflow-insight-velocity-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "none:9092");
        config.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toString());
        // application.yml disables the record cache so the stores reflect each record at once.
        config.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0L);

        driver = new TopologyTestDriver(builder.build(), config, T0);

        itemSold = driver.createInputTopic(ItemSoldEvent.TOPIC,
                new StringSerializer(), new ItemSoldEventSerde().serializer());
        baselines = driver.createInputTopic(VelocityBaselinePublishedEvent.TOPIC,
                new StringSerializer(), new VelocityBaselineSerde().serializer());
        batchReceived = driver.createInputTopic(BatchReceivedEvent.TOPIC,
                new StringSerializer(), new BatchReceivedEventSerde().serializer());
        stockLevelChanged = driver.createInputTopic(StockLevelChangedEvent.TOPIC,
                new StringSerializer(), new StockLevelChangedEventSerde().serializer());
        riskDetected = driver.createOutputTopic(SellThroughRiskDetectedEvent.TOPIC,
                new StringDeserializer(), new SellThroughRiskDetectedEventSerde().deserializer());
    }

    @AfterEach
    void closeTheDriver() {
        driver.close();
    }

    // ------------------------------------------------------------------------------------ FR12

    /**
     * The record timestamps are the epoch, a month earlier and a year later. If the topology
     * windowed on them, the three sales would land in three windows decades apart and the minute
     * from T0 would be empty. It holds all three, because the only time the topology reads is
     * {@code occurredAt}.
     */
    @Test
    @DisplayName("FR12: sales are summed into the window their occurredAt falls in, whatever the record timestamp says")
    void velocityWindows_arePlacedByOccurredAt_notByRecordTimestamp() {
        itemSold.pipeInput(null, sale(T0.plusSeconds(10), 1), Instant.EPOCH);
        itemSold.pipeInput(null, sale(T0.plusSeconds(20), 2), T0.minus(Duration.ofDays(30)));
        itemSold.pipeInput(null, sale(T0.plusSeconds(30), 3), T0.plus(Duration.ofDays(365)));

        Map<Instant, Long> windows = windowsStartingBetween(T0.minusSeconds(60), T0.plusSeconds(60));

        assertThat(windows.get(T0))
                .as("[T0, T0+60s) holds all three sales: 1 + 2 + 3")
                .isEqualTo(6L);
        assertThat(windows.get(T0.minusSeconds(30)))
                .as("[T0-30s, T0+30s) holds the first two; the sale at T0+30s is on the exclusive end")
                .isEqualTo(3L);
        assertThat(windows.get(T0.plusSeconds(15)))
                .as("[T0+15s, T0+75s) holds the last two")
                .isEqualTo(5L);

        assertThat(windowsStartingBetween(Instant.EPOCH.minusSeconds(60), Instant.EPOCH.plusSeconds(60)))
                .as("nothing was windowed at the record timestamp of the first sale")
                .isEmpty();
        assertThat(windowsStartingBetween(T0.minus(Duration.ofDays(30)).minusSeconds(60),
                T0.minus(Duration.ofDays(30)).plusSeconds(60)))
                .as("nothing was windowed at the record timestamp of the second sale")
                .isEmpty();
        assertThat(windowsStartingBetween(T0.plus(Duration.ofDays(365)).minusSeconds(60),
                T0.plus(Duration.ofDays(365)).plusSeconds(60)))
                .as("nothing was windowed at the record timestamp of the third sale")
                .isEmpty();
    }

    /**
     * The baseline table is not joined inside the topology; {@code velocityRatio} is computed on the
     * read side from this store (spec-05 &sect;4.2, &sect;5.1). What the topology owes the read side
     * is a table materialised under {@code BASELINE_STORE} with one row per SKU and the last write
     * winning, which is what lets the seeder republish on every restart as an idempotent overwrite.
     */
    @Test
    @DisplayName("FR12: the baseline KTable is materialised by name, one row per SKU, last write winning (ADR 0003)")
    void baselineTable_isMaterialisedByName_withTheLatestValuePerSku() {
        baselines.pipeInput(SKU, baseline(100.0, T0), T0);
        baselines.pipeInput(SKU, baseline(120.0, T0.plusSeconds(1)), T0.plusSeconds(1));

        KeyValueStore<String, VelocityBaselinePublishedEvent> store =
                driver.getKeyValueStore(BASELINE_STORE);

        assertThat(store.get(SKU)).isNotNull();
        assertThat(store.get(SKU).baselineVelocityPerHour())
                .as("the later publication replaced the earlier one")
                .isEqualTo(120.0);
        assertThat(store.get("SKU-OTHER"))
                .as("a SKU with no stored baseline has no row, not a row of zero (spec-05 s5.1)")
                .isNull();
    }

    // ------------------------------------------------------------------------------------ FR13

    /**
     * 500 units expiring in three hours, selling at one a minute: at most 180 or so can go before
     * expiry, so the batch is at risk from the first closed window. Eleven windows close at once
     * at {@code T0+70s} and all eleven agree; twelve more close at {@code T0+130s} and agree
     * again. One event.
     */
    @Test
    @DisplayName("FR13: a batch that cannot clear before expiry is detected once, when the window closes, and not again while it stays at risk")
    void riskDetected_firesExactlyOnce_onTheTransitionIntoRisk() {
        batchReceived.pipeInput("BAT-0001", received("BAT-0001", 500, T0), T0);

        itemSold.pipeInput(null, sale(T0.plusSeconds(10), 1), T0.plusSeconds(10));
        assertThat(riskDetected.isEmpty())
                .as("nothing is published while the window is still open (suppress until close)")
                .isTrue();

        // Stream time moves to T0+70s; every window that saw the sale and ends by T0+65s closes.
        itemSold.pipeInput(null, sale(T0.plusSeconds(70), 1), T0.plusSeconds(70));

        List<SellThroughRiskDetectedEvent> events = riskDetected.readValuesToList();
        assertThat(events)
                .as("eleven closed windows assessed the batch; one transition into risk was published")
                .hasSize(1);

        SellThroughRiskDetectedEvent event = events.get(0);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.batchId()).isEqualTo("BAT-0001");
        assertThat(event.sku()).isEqualTo(SKU);
        assertThat(event.remainingQuantity()).isEqualTo(500);
        assertThat(event.currentVelocity())
                .as("one unit in a 60-second window is 60 an hour")
                .isCloseTo(60.0, within(1e-9));
        assertThat(event.hoursToExpiry())
                .as("measured from the window end, not from a clock: 2h59m45s truncates to 2")
                .isEqualTo(2L);
        assertThat(event.projectedShortfall())
                .as("500 remaining minus 60/h over 2 hours")
                .isCloseTo(380.0, within(1e-6));
        assertThat(event.occurredAt())
                .as("event time: the end of the first window to close, [T0-45s, T0+15s)")
                .isEqualTo(T0.plusSeconds(15));

        // Another minute at the same rate: the next windows close with the same verdict.
        itemSold.pipeInput(null, sale(T0.plusSeconds(130), 1), T0.plusSeconds(130));
        assertThat(riskDetected.isEmpty())
                .as("still at risk is not newly at risk; no second event")
                .isTrue();

        RiskAssessment standing = risks().get("BAT-0001");
        assertThat(standing).isNotNull();
        assertThat(standing.view().atRisk())
                .as("the read model behind GET /api/insight/risks still lists it")
                .isTrue();
        assertThat(standing.newlyAtRisk())
                .as("the stored assessment is the latest one, and it is a repeat")
                .isFalse();
    }

    /**
     * Ten units, three hours, sixty an hour: the batch clears many times over. The topology must
     * assess it and find it safe, and say nothing. The store proves the assessment happened, so that
     * silence is a verdict and not a join that never fired.
     */
    @Test
    @DisplayName("FR13: a batch that clears at the observed rate is assessed, found safe, and no event is published")
    void riskDetected_staysSilent_forABatchThatWillClear() {
        batchReceived.pipeInput("BAT-0002", received("BAT-0002", 10, T0), T0);
        itemSold.pipeInput(null, sale(T0.plusSeconds(10), 1), T0.plusSeconds(10));
        itemSold.pipeInput(null, sale(T0.plusSeconds(70), 1), T0.plusSeconds(70));

        assertThat(riskDetected.isEmpty())
                .as("a batch that will clear is not a detection")
                .isTrue();

        RiskAssessment stored = risks().get("BAT-0002");
        assertThat(stored)
                .as("the batch WAS assessed; the silence is a verdict, not an absence")
                .isNotNull();
        assertThat(stored.view().atRisk()).isFalse();
        assertThat(stored.view().projectedShortfall())
                .as("10 remaining minus 60/h over 2 hours: clears with room to spare, not clamped")
                .isCloseTo(-110.0, within(1e-6));
    }

    /**
     * The fourth input. A batch received at 500 whose remaining quantity has since fallen to 10 is
     * assessed at 10; a stock change for a batch never received establishes nothing (spec-05
     * &sect;3.1).
     */
    @Test
    @DisplayName("FR13: stock-level-changed updates what remains before assessment; an unseen batch is dropped")
    void batchState_foldsStockLevelChanged_andDropsAnUnseenBatch() {
        batchReceived.pipeInput("BAT-0003", received("BAT-0003", 500, T0), T0);
        stockLevelChanged.pipeInput("BAT-0003", stockChanged("BAT-0003", 10, T0.plusSeconds(5)),
                T0.plusSeconds(5));
        stockLevelChanged.pipeInput("BAT-9999", stockChanged("BAT-9999", 400, T0.plusSeconds(6)),
                T0.plusSeconds(6));

        itemSold.pipeInput(null, sale(T0.plusSeconds(10), 1), T0.plusSeconds(10));
        itemSold.pipeInput(null, sale(T0.plusSeconds(70), 1), T0.plusSeconds(70));

        assertThat(riskDetected.isEmpty())
                .as("10 remaining at 60/h clears; the 500 it was received at is history")
                .isTrue();
        assertThat(risks().get("BAT-0003").view().remainingQuantity()).isEqualTo(10);
        assertThat(risks().get("BAT-9999"))
                .as("no batch-received, no expiry, no assessment")
                .isNull();
    }

    // -------------------------------------------------------------------------------- fixtures

    private static ItemSoldEvent sale(Instant occurredAt, int quantity) {
        return new ItemSoldEvent(UUID.randomUUID(), occurredAt, "TXN-" + occurredAt.getEpochSecond(),
                SKU, quantity, UNIT_PRICE, occurredAt);
    }

    private static BatchReceivedEvent received(String batchId, int quantity, Instant occurredAt) {
        return new BatchReceivedEvent(UUID.randomUUID(), occurredAt, batchId, SKU, quantity,
                EXPIRY_AT, UNIT_COST);
    }

    private static StockLevelChangedEvent stockChanged(String batchId, int remaining,
                                                       Instant occurredAt) {
        return new StockLevelChangedEvent(UUID.randomUUID(), occurredAt, batchId, SKU, remaining,
                occurredAt);
    }

    private static VelocityBaselinePublishedEvent baseline(double perHour, Instant occurredAt) {
        return new VelocityBaselinePublishedEvent(UUID.randomUUID(), occurredAt, SKU, perHour);
    }

    /** FR12's window store, read as the Interactive Query adapter reads it: by window start. */
    private Map<Instant, Long> windowsStartingBetween(Instant from, Instant to) {
        WindowStore<String, Long> store = driver.getWindowStore(WINDOW_STORE);
        Map<Instant, Long> byStart = new LinkedHashMap<>();
        try (WindowStoreIterator<Long> windows = store.fetch(SKU, from, to)) {
            while (windows.hasNext()) {
                KeyValue<Long, Long> window = windows.next();
                byStart.put(Instant.ofEpochMilli(window.key), window.value);
            }
        }
        return byStart;
    }

    /** FR13's standing assessments, the read model behind {@code GET /api/insight/risks}. */
    private KeyValueStore<String, RiskAssessment> risks() {
        return driver.getKeyValueStore(RISK_STORE);
    }
}
