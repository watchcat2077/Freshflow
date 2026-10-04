package com.freshflow.pricing.application;

import java.math.BigDecimal;

/**
 * Read model of a margin floor refusal (FR11) &mdash; the numbers spec-04's
 * {@code MarginFloorRejection} reports, carried outward from the domain's
 * {@code MarginFloorAssessment} without being recomputed (spec-01 &sect;2.5, team ruling B-3).
 *
 * <p>{@code markdownPlanId} is the REJECTED plan that records this refusal, so that "the rejection
 * is persisted and queryable by its id" (spec-04) is something the caller can actually do.
 */
public record MarginFloorRejectionView(String markdownPlanId,
                                       String sku,
                                       String requestedTier,
                                       BigDecimal resultingPriceAmount,
                                       String resultingPriceCurrency,
                                       BigDecimal unitCostAmount,
                                       String unitCostCurrency,
                                       BigDecimal resultingMarginRatio,
                                       BigDecimal marginFloorRatio,
                                       String message) {
}
