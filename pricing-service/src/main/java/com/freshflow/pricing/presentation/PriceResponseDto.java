package com.freshflow.pricing.presentation;

import java.math.BigDecimal;

/**
 * spec-04 {@code PriceResponse}.
 *
 * <p>{@code activeDiscountTier} is declared nullable by spec-04: it names the tier of the markdown
 * plan in force at the moment of the request, and is null when none is (FR8, extended 4 October
 * 2026). {@code effectivePrice} is that plan's discounted price, or the base price when there is
 * no plan in force.
 */
public record PriceResponseDto(String sku,
                               String category,
                               MoneyDto basePrice,
                               MoneyDto unitCost,
                               MoneyDto effectivePrice,
                               BigDecimal marginFloorRatio,
                               String activeDiscountTier) {
}
