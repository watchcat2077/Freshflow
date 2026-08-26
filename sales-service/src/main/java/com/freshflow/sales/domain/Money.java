package com.freshflow.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Amount in a currency. Re-declared independently in this context: spec-03 &sect;6 states there is
 * deliberately no shared kernel.
 *
 * <p>{@link #multiply} and {@link #add} are value arithmetic, not business rules. Which price
 * applies to a scan is the business decision, and that is made by the transaction script in the
 * application layer, where spec-01 &sect;2.5 places this service's invariants.
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
        // spec-03 s7: normalise on construction, so the same amount renders identically no
        // matter which path produced it -- straight from a request, or read back from a column
        // with a different scale. Also makes record equality behave sanely, since BigDecimal
        // equals() is scale-sensitive and 2.10 does not equal 2.1000.
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount), currency);
    }
}
