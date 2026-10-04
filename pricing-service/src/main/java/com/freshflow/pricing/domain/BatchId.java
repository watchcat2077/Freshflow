package com.freshflow.pricing.domain;

import java.util.Objects;

/**
 * Value Object (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; the batch a markdown plan is for.
 *
 * <p>Re-declared in this context (spec-03 &sect;6: no shared kernel). To Pricing a batch is only an
 * identifier that a plan refers to; its quantity, expiry and cost are Inventory's concern, and this
 * context never reads them. It does not mint batch ids either: a plan is always <i>for</i> a batch
 * that already exists somewhere else, so there is no {@code newId()} here.
 */
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
}
