package com.freshflow.pricing.presentation;

import java.math.BigDecimal;

/** spec-04 {@code UpsertPriceRuleRequest}. The SKU is the path variable, not a body field. */
public record UpsertPriceRuleRequestDto(MoneyDto basePrice,
                                        MoneyDto unitCost,
                                        String category,
                                        BigDecimal marginFloorRatio) {
}
