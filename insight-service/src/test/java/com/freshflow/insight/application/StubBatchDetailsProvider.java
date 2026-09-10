package com.freshflow.insight.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test adapter for the {@code BatchDetailsProvider} port.
 *
 * <p>This class is the concrete form of spec-06 &sect;3's claim that the unit-cost ruling is
 * deferrable. Every FR14 test reaches batch facts through this stub, so no test asserts anything
 * about where those facts come from; whether Phase B carries {@code unitCost} through the
 * batch-state table or fetches it over REST from {@code inventory-service}, not one line below
 * changes.
 *
 * <p>An unknown batch yields an empty result, because that is what the port promises and what the
 * {@code getBatchDetails} tool has to cope with (spec-06 &sect;4).
 */
public final class StubBatchDetailsProvider implements BatchDetailsProvider {

    private final Map<String, BatchDetails> byBatchId = new LinkedHashMap<>();

    public StubBatchDetailsProvider with(BatchDetails details) {
        byBatchId.put(details.batchId(), details);
        return this;
    }

    @Override
    public Optional<BatchDetails> findBatch(String batchId) {
        return Optional.ofNullable(byBatchId.get(batchId));
    }
}
