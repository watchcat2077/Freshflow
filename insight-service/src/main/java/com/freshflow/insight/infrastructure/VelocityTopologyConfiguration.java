package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
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

import java.util.function.BiConsumer;

/**
 * FR12 &mdash; the Kafka Streams topology behind the rolling sell-through velocity.
 *
 * <p>Two inputs, one function, so that both state stores belong to one topology and one
 * {@code application.id}, and Interactive Query can reach both:
 * <ol>
 *   <li>{@code item-sold} as a {@link KStream} &mdash; the event stream, keyed by SKU and
 *       aggregated over a hopping window.</li>
 *   <li>{@code velocity-baseline-published} as a {@link KTable} &mdash; FR12's "stored velocity
 *       baseline held in a KTable", seeded from configuration by
 *       {@link VelocityBaselineSeeder}.</li>
 * </ol>
 *
 * <p><b>Event time, not ingestion time.</b> The window is placed by
 * {@link OccurredAtTimestampExtractor}, wired to the input binding in {@code application.yml}.
 * spec-01 &sect;2.6 requires this and it is the one thing in FR12 that cannot be compromised; see
 * that class for what goes wrong otherwise.
 *
 * <p><b>Where the comparison happens.</b> The topology maintains the two stores; the ratio is
 * computed by {@code VelocityQueryService} when the endpoint is called. FR12 asks that the
 * velocity be compared against a baseline held in a KTable, and it is &mdash; but it does not
 * require the comparison to be a stream-table join, and doing it at read time is both simpler and
 * better here. A join would emit a view per window per SKU whether or not anyone ever asks, and
 * would have to guess at query time which of the twelve overlapping windows the emitted view came
 * from. FR13's risk detection is where a genuine stream-table join belongs, because there the
 * output is an event that must be published whether or not anyone is watching. That is next week.
 *
 * <p>The whole class is conditional on {@code freshflow.messaging.kafka.enabled}, which defaults to
 * true; only the integration-test profile switches it off.
 *
 * <p>The class is named {@code ...Configuration} while the bean method is named
 * {@code velocityTopology}, and the two must not be made to match: Spring derives a bean name from
 * the configuration class itself, and Spring Cloud Stream derives the binding names
 * {@code velocityTopology-in-0} and {@code -in-1} from the function bean's name. Naming the class
 * {@code VelocityTopology} makes those two collide, and the integration-test profile cannot catch
 * it because it switches this class off.
 */
@Configuration
@EnableConfigurationProperties(VelocityProperties.class)
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class VelocityTopologyConfiguration {

    /** Windowed aggregate: units sold per SKU per window. Queried by Interactive Query. */
    public static final String WINDOW_STORE = "velocity-windows";

    /** The KTable's state store: the stored baseline per SKU. */
    public static final String BASELINE_STORE = "velocity-baselines";

    private static final Logger log = LoggerFactory.getLogger(VelocityTopologyConfiguration.class);

    private final VelocityProperties properties;

    public VelocityTopologyConfiguration(VelocityProperties properties) {
        this.properties = properties;
    }

    /**
     * The extractor bean the {@code item-sold} binding names in {@code application.yml}
     * ({@code timestampExtractorBeanName}).
     */
    @Bean
    public TimestampExtractor occurredAtTimestampExtractor() {
        return new OccurredAtTimestampExtractor();
    }

    @Bean
    public BiConsumer<KStream<String, ItemSoldEvent>, KTable<String, VelocityBaselinePublishedEvent>>
            velocityTopology() {

        return (itemSold, baselines) -> {
            log.info("FR12 topology: window={}s advancing every {}s, grace {}s, retention {}min; "
                            + "windowing on producer-assigned occurredAt (spec-01 s2.6)",
                    properties.getWindowSeconds(), properties.getAdvanceSeconds(),
                    properties.getGraceSeconds(), properties.getRetentionMinutes());

            // FR12: "consumes item-sold, keys by SKU". Keying explicitly rather than trusting the
            // producer's message key -- sales-service does set it to the SKU, but the aggregate
            // must be correct for any producer that reaches this topic, including a hand-run
            // console producer during a demo.
            itemSold
                    .filter((key, event) -> event != null && event.sku() != null)
                    .groupBy((key, event) -> event.sku(),
                            Grouped.with(Serdes.String(), new ItemSoldEventSerde()))
                    // A hopping window: 60 seconds advancing every 5 by default. spec-02 FR12's
                    // MVP simplification of the 60-minute/5-minute production target, same ratio.
                    .windowedBy(TimeWindows.ofSizeAndGrace(properties.window(), properties.grace())
                            .advanceBy(properties.advance()))
                    .aggregate(
                            () -> 0L,
                            (sku, event, total) -> total + event.quantity(),
                            Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(WINDOW_STORE)
                                    .withKeySerde(Serdes.String())
                                    .withValueSerde(Serdes.Long())
                                    // Retention must outlast what a query looks back over, or the
                                    // endpoint would 404 on windows the store had already dropped.
                                    .withRetention(properties.retention()));

            // FR12's KTable. Materialised explicitly, with the tombstone filter doubling as the
            // reason there is a Materialized here at all: a store this service can query by name.
            baselines.filter((sku, baseline) -> baseline != null,
                    Materialized.<String, VelocityBaselinePublishedEvent, KeyValueStore<Bytes, byte[]>>
                                    as(BASELINE_STORE)
                            .withKeySerde(Serdes.String())
                            .withValueSerde(new VelocityBaselineSerde()));
        };
    }
}
