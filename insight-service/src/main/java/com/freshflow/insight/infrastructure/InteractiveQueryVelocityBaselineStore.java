package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.VelocityBaselineStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Adapter for the {@code VelocityBaselineStore} port &mdash; reads FR12's baseline out of the
 * {@code KTable}'s state store by Interactive Query.
 *
 * <p>This is the class that makes "held in a KTable" true rather than decorative: the baseline the
 * endpoint compares against is read from the table the topology maintains, not from the
 * configuration file the table was seeded from. Change the seed source later and nothing here
 * moves.
 *
 * <p>An unavailable store and an unknown SKU are both reported as an absent baseline. Neither is an
 * error, and neither may become a zero &mdash; see {@code SkuVelocityView} for why a fabricated
 * baseline of zero would be worse than no baseline at all.
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class InteractiveQueryVelocityBaselineStore implements VelocityBaselineStore {

    private static final Logger log =
            LoggerFactory.getLogger(InteractiveQueryVelocityBaselineStore.class);

    private final InteractiveQueryService interactiveQueryService;

    public InteractiveQueryVelocityBaselineStore(InteractiveQueryService interactiveQueryService) {
        this.interactiveQueryService = interactiveQueryService;
    }

    @Override
    public OptionalDouble baselineFor(String sku) {
        ReadOnlyKeyValueStore<String, VelocityBaselinePublishedEvent> store;
        try {
            store = interactiveQueryService.getQueryableStore(
                    InsightTopologyConfiguration.BASELINE_STORE, QueryableStoreTypes.keyValueStore());
        } catch (RuntimeException notReadyYet) {
            log.debug("State store {} is not queryable yet: {}",
                    InsightTopologyConfiguration.BASELINE_STORE, notReadyYet.getMessage());
            return OptionalDouble.empty();
        }
        if (store == null) {
            return OptionalDouble.empty();
        }

        VelocityBaselinePublishedEvent baseline = store.get(sku);
        return baseline == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(baseline.baselineVelocityPerHour());
    }
}
