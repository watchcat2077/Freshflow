package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.WindowedQuantity;
import com.freshflow.insight.domain.BatchRiskView;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * In the first seconds after start-up the Kafka Streams thread is STARTING, and a state store it
 * hands out throws {@link InvalidStateStoreException} from its iterator rather than from
 * {@code getQueryableStore}. A query that arrives then has nothing to show yet, which is not an
 * error: the velocity endpoint answers its documented 404 and the risk endpoint an empty list.
 * Found by the live run of 13 October 2026, whose health probe got a 500.
 */
class InteractiveQueryStoresNotReadyTest {

    private static final InvalidStateStoreException STARTING = new InvalidStateStoreException(
            "Cannot get state store batch-risks because the stream thread is STARTING, not RUNNING");

    @Test
    @SuppressWarnings("unchecked")
    void riskViews_areEmpty_whileTheStreamThreadIsStillStarting() {
        InteractiveQueryService queries = mock(InteractiveQueryService.class);
        ReadOnlyKeyValueStore<String, RiskAssessment> store = mock(ReadOnlyKeyValueStore.class);
        when(store.all()).thenThrow(STARTING);
        when(queries.getQueryableStore(eq(InsightTopologyConfiguration.RISK_STORE), any(QueryableStoreType.class)))
                .thenReturn(store);

        List<BatchRiskView> views = new InteractiveQueryRiskViewStore(queries).findAll();

        assertThat(views).as("a store that is not ready yet has nothing to show; it is not an error").isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void velocityWindows_areEmpty_whileTheStreamThreadIsStillStarting() {
        InteractiveQueryService queries = mock(InteractiveQueryService.class);
        ReadOnlyWindowStore<String, Long> store = mock(ReadOnlyWindowStore.class);
        when(store.fetch(anyString(), any(Instant.class), any(Instant.class))).thenThrow(STARTING);
        when(queries.getQueryableStore(eq(InsightTopologyConfiguration.WINDOW_STORE), any(QueryableStoreType.class)))
                .thenReturn(store);

        List<WindowedQuantity> windows = new InteractiveQueryVelocityWindowStore(queries, new VelocityProperties())
                .fetchWindows("SKU-YOG-1KG", Instant.EPOCH, Instant.EPOCH.plusSeconds(600));

        assertThat(windows).as("no closed window can be served before the store is queryable").isEmpty();
    }
}
