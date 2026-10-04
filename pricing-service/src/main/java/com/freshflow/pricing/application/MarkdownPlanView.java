package com.freshflow.pricing.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model of a {@code MarkdownPlan} returned by the application layer, so that
 * {@code presentation} never handles the aggregate directly.
 *
 * <p>{@code discountedPriceAmount} and {@code discountedPriceCurrency} are null unless the plan is
 * ACTIVE; {@code rejectionReason} is null unless it is REJECTED; {@code agentTraceId} and
 * {@code rationale} are null when the proposer gave none.
 */
public record MarkdownPlanView(String markdownPlanId,
                               String batchId,
                               String sku,
                               String discountTier,
                               Instant effectiveFrom,
                               Instant effectiveTo,
                               String status,
                               String origin,
                               BigDecimal discountedPriceAmount,
                               String discountedPriceCurrency,
                               String rationale,
                               String agentTraceId,
                               String rejectionReason,
                               Instant createdAt) {
}
