package com.freshflow.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Value Object (spec-01 &sect;2.4) &mdash; the instant at which a batch expires. */
public record ExpiryDate(Instant value) {

    public ExpiryDate {
        Objects.requireNonNull(value, "value");
    }

    public static ExpiryDate of(Instant value) {
        return new ExpiryDate(value);
    }

    public boolean isAfter(Instant instant) {
        return value.isAfter(instant);
    }

    public boolean hasPassedBy(Instant instant) {
        return !value.isAfter(instant);
    }

    /** Never negative: an expired batch has no shelf life left, rather than a negative one. */
    public Duration remainingShelfLifeAt(Instant now) {
        Duration remaining = Duration.between(now, value);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
