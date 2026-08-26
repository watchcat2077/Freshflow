package com.freshflow.sales.domain;

import java.math.BigDecimal;
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

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
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
