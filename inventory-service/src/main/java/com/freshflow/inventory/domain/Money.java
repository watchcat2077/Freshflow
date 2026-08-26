package com.freshflow.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object (spec-01 &sect;2.4) &mdash; an amount in a currency.
 *
 * <p>Re-declared independently in this context rather than shared: spec-03 &sect;6 states there is
 * deliberately no shared kernel.
 */
public record Money(BigDecimal amount, String currency) {

    /**
     * Currency scale for this project: 2 decimal places, {@link RoundingMode#HALF_UP}
     * (spec-03 &sect;7).
     */
    public static final int SCALE = 2;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.isBlank()) {
            throw new DomainRuleViolation("CURRENCY_REQUIRED", "Currency must not be blank.");
        }
        if (amount.signum() < 0) {
            throw new DomainRuleViolation("NEGATIVE_AMOUNT", "Money amount must not be negative.");
        }
        // spec-03 s7: normalise on construction, so the same amount renders identically no
        // matter which path produced it -- straight from a request, or read back from a column
        // with a different scale. Also makes record equality behave sanely, since BigDecimal
        // equals() is scale-sensitive and 2.10 does not equal 2.1000.
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
