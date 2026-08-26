package com.freshflow.inventory.domain;

/**
 * Value Object (spec-01 &sect;2.4) &mdash; a non-negative count of units (spec-03 &sect;1).
 *
 * <p>Zero is representable because a depleted batch has zero remaining. A goods receipt of zero
 * is a separate rule and is rejected by {@link StockBatch#receive}, not here.
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 0) {
            throw new DomainRuleViolation("NEGATIVE_QUANTITY", "Quantity must not be negative.");
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public boolean isPositive() {
        return value > 0;
    }

    public boolean isZero() {
        return value == 0;
    }

    public Quantity minus(Quantity other) {
        return new Quantity(this.value - other.value);
    }

    public boolean isLessThan(Quantity other) {
        return this.value < other.value;
    }
}
