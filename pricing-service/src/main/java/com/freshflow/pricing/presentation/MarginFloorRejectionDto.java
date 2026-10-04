package com.freshflow.pricing.presentation;

import java.math.BigDecimal;

/**
 * spec-04 {@code MarginFloorRejection}, returned with HTTP 422.
 *
 * <p>{@code error} is always {@code MARGIN_FLOOR_BREACH}. {@code markdownPlanId} is one field more
 * than the contract lists: spec-04 says the rejection "is persisted as a REJECTED plan and is
 * queryable by its id", and without the id in the response there is no way for the caller to do
 * the querying. It is additive; every contract field is present and unchanged.
 */
public record MarginFloorRejectionDto(String error,
                                      String markdownPlanId,
                                      String sku,
                                      String requestedTier,
                                      MoneyDto resultingPrice,
                                      MoneyDto unitCost,
                                      BigDecimal resultingMarginRatio,
                                      BigDecimal marginFloorRatio,
                                      String message) {

    public static final String ERROR = "MARGIN_FLOOR_BREACH";
}
