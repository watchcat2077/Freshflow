package com.freshflow.pricing.domain;

import java.util.Objects;

/** Value Object (spec-01 &sect;2.4) &mdash; a stock-keeping unit identifier. */
public record Sku(String value) {

    public Sku {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new DomainRuleViolation("SKU_REQUIRED", "SKU must not be blank.");
        }
    }

    public static Sku of(String value) {
        return new Sku(value);
    }
}
