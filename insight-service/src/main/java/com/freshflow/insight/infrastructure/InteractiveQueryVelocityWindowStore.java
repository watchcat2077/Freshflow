package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.VelocityWindowStore;
import com.freshflow.insight.application.WindowedQuantity;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the {@code VelocityWindowStore} port &mdash; reads the windowed aggregate out of the
 * Kafka Streams state store by <b>Interactive Query</b>, which is what spec-04 names for
 * {@code GET /api/insight/velocity/&#123;sku&#125;}.
 *
 * <p>Interactive Query is the read half of CQRS here: the topology writes the view continuously,
 * and this reads it from the local state store with no round trip to the broker. That is why the
 * endpoint can answer in a millisecond from a stream that never stops moving.
 *
 * <p>It contains no judgement and no arithmetic. It returns every window overlapping the requested
 * range and lets {@code VelocityQueryService} decide which one is current, so that the decision
 * lives somewhere a test can reach without a broker.
 *
 * <p>A store that is not there yet is not an error. During startup, rebalancing, or state
 * restoration the store is genuinely unavailable, and the honest answer is "no windowed data",
 * which the endpoint renders as spec-04's 404 rather than as a 500.
 *
 * <p>Plays the role of {@code InteractiveQuery} in the subject's reference application
 * {@code cargo-tracker-0.1} ({@code analyticsms}): {@code InteractiveQueryService.getQueryableStore(...)} over a window
 * store, here behind an application-layer port so that the query service and the agent's tool
 * read FR12 through one rule.
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class InteractiveQueryVelocityWindowStore implements VelocityWindowStore {

    private static final Logger log =
            LoggerFactory.getLogger(InteractiveQueryVelocityWindowStore.class);

    private final InteractiveQueryService interactiveQueryService;
    private final VelocityProperties properties;

    public InteractiveQueryVelocityWindowStore(InteractiveQueryService interactiveQueryService,
                                               VelocityProperties properties) {
        this.interactiveQueryService = interactiveQueryService;
        this.properties = properties;
    }

    @Override
    public List<WindowedQuantity> fetchWindows(String sku, Instant from, Instant to) {
        ReadOnlyWindowStore<String, Long> store;
        try {
            store = interactiveQueryService.getQueryableStore(
                    InsightTopologyConfiguration.WINDOW_STORE, QueryableStoreTypes.windowStore());
        } catch (RuntimeException notReadyYet) {
            log.debug("State store {} is not queryable yet: {}",
                    InsightTopologyConfiguration.WINDOW_STORE, notReadyYet.getMessage());
            return List.of();
        }
        if (store == null) {
            return List.of();
        }

        List<WindowedQuantity> windows = new ArrayList<>();
        // fetch(key, from, to) ranges over window START times, and yields the window start as the
        // iterator key. The end is start + the configured size.
        try (WindowStoreIterator<Long> iterator = store.fetch(sku, from, to)) {
            while (iterator.hasNext()) {
                KeyValue<Long, Long> entry = iterator.next();
                Instant windowStart = Instant.ofEpochMilli(entry.key);
                windows.add(new WindowedQuantity(
                        windowStart, windowStart.plus(properties.window()), entry.value));
            }
        } catch (InvalidStateStoreException notReadyYet) {
            // The stream thread is still STARTING: the store exists but cannot be read yet.
            // No window can be served, which the caller reports as its documented 404.
            log.debug("State store {} is not readable yet: {}",
                    InsightTopologyConfiguration.WINDOW_STORE, notReadyYet.getMessage());
            return List.of();
        }
        return windows;
    }
}
