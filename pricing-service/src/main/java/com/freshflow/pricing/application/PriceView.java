package com.freshflow.pricing.application;

import java.math.BigDecimal;

/**
 * Read model returned by the application layer, so that {@code presentation} never handles a
 * domain entity directly.
 */
public record PriceView(String sku,
                        String category,
                        BigDecimal basePriceAmount,
                        String basePriceCurrency,
                        BigDecimal unitCostAmount,
                        String unitCostCurrency,
                        BigDecimal effectivePriceAmount,
                        String effectivePriceCurrency,
                        BigDecimal marginFloorRatio,
                        String activeDiscountTier) {
}
