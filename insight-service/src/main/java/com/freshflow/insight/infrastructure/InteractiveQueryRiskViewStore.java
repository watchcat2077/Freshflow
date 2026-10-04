package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.RiskViewStore;
import com.freshflow.insight.domain.BatchRiskView;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the {@code RiskViewStore} port — reads FR13's assessments out of the Kafka Streams
 * state store by Interactive Query, the same mechanism spec-04 names for the velocity endpoint.
 *
 * <p>A full scan of the store, which is correct here and would not be in a larger system: the
 * store holds one row per batch this instance has assessed, and {@code GET /api/insight/risks}
 * is defined as a list rather than a lookup. There is no key to query by.
 *
 * <p>It returns every assessment, at risk or not, and leaves the filtering to the application
 * service. A store that is not ready yet yields an empty list, which the endpoint renders as
 * {@code []} — during startup or state restoration "nothing is currently flagged" is the honest
 * answer, and a 500 would not be.
 */
@Component
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class InteractiveQueryRiskViewStore implements RiskViewStore {

    private static final Logger log = LoggerFactory.getLogger(InteractiveQueryRiskViewStore.class);

    private final InteractiveQueryService interactiveQueryService;

    public InteractiveQueryRiskViewStore(InteractiveQueryService interactiveQueryService) {
        this.interactiveQueryService = interactiveQueryService;
    }

    @Override
    public List<BatchRiskView> findAll() {
        ReadOnlyKeyValueStore<String, RiskAssessment> store;
        try {
            store = interactiveQueryService.getQueryableStore(
                    InsightTopologyConfiguration.RISK_STORE, QueryableStoreTypes.keyValueStore());
        } catch (RuntimeException notReadyYet) {
            log.debug("State store {} is not queryable yet: {}",
                    InsightTopologyConfiguration.RISK_STORE, notReadyYet.getMessage());
            return List.of();
        }
        if (store == null) {
            return List.of();
        }

        List<BatchRiskView> views = new ArrayList<>();
        try (KeyValueIterator<String, RiskAssessment> all = store.all()) {
            while (all.hasNext()) {
                KeyValue<String, RiskAssessment> entry = all.next();
                if (entry.value != null && entry.value.view() != null) {
                    views.add(entry.value.view());
                }
            }
        }
        return views;
    }
}
