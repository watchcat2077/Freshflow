package com.freshflow.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object (spec-01 &sect;2.4). Re-declared independently in this context: spec-03 &sect;6 states
 * there is deliberately no shared kernel.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.isBlank()) {
            throw new DomainRuleViolation("CURRENCY_REQUIRED", "Currency must not be blank.");
        }
        if (amount.signum() < 0) {
            throw new DomainRuleViolation("NEGATIVE_AMOUNT", "Money amount must not be negative.");
        }
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
