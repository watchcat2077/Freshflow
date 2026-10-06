package com.freshflow.pricing.application;

import java.math.BigDecimal;

/**
 * Command (spec-01 s2.1, L5 slide 7: a simple DTO handled in the application service layer, which does not enter
 * the domain layer) &mdash; create or replace the price rule and margin floor for a SKU (FR8
 * beyond the seeded minimum; spec-04 {@code upsertPriceRule}).
 *
 * <p>The SKU comes from the path and the rest from the body; the controller joins them here so the
 * application layer sees one command however the endpoint is shaped.
 */
public record UpsertPriceRuleCommand(String sku,
                                     BigDecimal basePriceAmount,
                                     String basePriceCurrency,
                                     BigDecimal unitCostAmount,
                                     String unitCostCurrency,
                                     String category,
                                     BigDecimal marginFloorRatio) {
}
