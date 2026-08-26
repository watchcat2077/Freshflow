package com.freshflow.pricing.presentation;

import java.math.BigDecimal;

/**
 * spec-04 {@code PriceResponse}.
 *
 * <p>{@code activeDiscountTier} is declared nullable by spec-04 and is null throughout Week 5,
 * since no markdown can be active before FR9 is built.
 */
public record PriceResponseDto(String sku,
                               String category,
                               MoneyDto basePrice,
                               MoneyDto unitCost,
                               MoneyDto effectivePrice,
                               BigDecimal marginFloorRatio,
                               String activeDiscountTier) {
}
