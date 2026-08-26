package com.freshflow.sales.domain;

import java.util.Objects;

/** A stock-keeping unit identifier. */
public record Sku(String value) {

    public Sku {
        Objects.requireNonNull(value, "value");
    }

    public static Sku of(String value) {
        return new Sku(value);
    }
}
