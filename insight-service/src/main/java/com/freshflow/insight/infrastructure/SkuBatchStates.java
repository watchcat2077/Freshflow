package com.freshflow.insight.infrastructure;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The live batch state for one SKU: every batch of it the topology has heard about, and what is
 * left of each.
 *
 * <p>This is the value of FR13's "{@code KTable} of live batch state built from
 * {@code batch-received} and {@code stock-level-changed}". It is keyed by SKU rather than by batch
 * because FR13 joins it against the <b>velocity stream</b>, and velocity is a per-SKU measurement.
 * A table keyed by batch could not be joined against a per-SKU stream at all; a table keyed by SKU
 * holding a single batch would silently discard every batch but the last. Holding the whole set is
 * what makes the join both possible and correct, and it is why one velocity update assesses every
 * batch of that SKU at once.
 *
 * <p>Two event types fold into it, and neither is sufficient alone: {@code batch-received} supplies
 * a batch's expiry and opening quantity, {@code stock-level-changed} supplies every subsequent
 * change to what remains. That is the whole reason FR2 had to be built before FR13.
 */
public record SkuBatchStates(Map<String, BatchState> batches) {

    public SkuBatchStates {
        batches = batches == null ? Map.of() : Map.copyOf(batches);
    }

    public static SkuBatchStates empty() {
        return new SkuBatchStates(Map.of());
    }

    /**
     * Folds one update in, returning a new value.
     *
     * <p>A stock change for a batch this table has never seen received is <b>dropped</b>, because
     * without {@code batch-received} there is no expiry, and a batch with no expiry cannot be
     * assessed for expiry risk. Recording it with a guessed expiry would be worse than not
     * recording it: it would produce a risk figure that looks authoritative and is invented.
     */
    public SkuBatchStates apply(BatchStateUpdate update) {
        Map<String, BatchState> next = new LinkedHashMap<>(batches);
        BatchState existing = next.get(update.batchId());

        if (update.expiryAt() != null) {
            // batch-received: everything is known, so this establishes or replaces the state.
            next.put(update.batchId(), new BatchState(update.batchId(), update.sku(),
                    update.expiryAt(), update.remainingQuantity(), update.at()));
        } else if (existing != null) {
            // stock-level-changed: only the remaining quantity moved.
            next.put(update.batchId(), new BatchState(existing.batchId(), existing.sku(),
                    existing.expiryAt(), update.remainingQuantity(), update.at()));
        } else {
            return this;
        }

        return new SkuBatchStates(next);
    }

    /** One batch as the table currently understands it. */
    public record BatchState(String batchId, String sku, Instant expiryAt, int remainingQuantity,
                             Instant updatedAt) {
    }

    /**
     * A change to fold in. {@code expiryAt} is present only on updates derived from
     * {@code batch-received}; its absence is what distinguishes the two sources after they have
     * been merged into one stream.
     */
    public record BatchStateUpdate(String batchId, String sku, Instant expiryAt,
                                   int remainingQuantity, Instant at) {
    }
}
