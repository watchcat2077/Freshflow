package com.freshflow.inventory.presentation;

import java.math.BigDecimal;

/** spec-04 {@code MoneyDto}. */
public record MoneyDto(BigDecimal amount, String currency) {
}
