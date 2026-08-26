package com.freshflow.inventory.domain;

import java.util.Objects;
import java.util.UUID;

/** Value Object (spec-01 &sect;2.4) &mdash; identity of a {@link StockBatch}. */
public record BatchId(String value) {

    public BatchId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new DomainRuleViolation("BATCH_ID_REQUIRED", "Batch id must not be blank.");
        }
    }

    public static BatchId of(String value) {
        return new BatchId(value);
    }

    /** spec-04 illustrates the {@code BAT-} prefix; the suffix only has to be unique. */
    public static BatchId newId() {
        return new BatchId("BAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}
