package com.freshflow.pricing.presentation;

import java.time.Instant;

/**
 * spec-04 {@code CreateMarkdownPlanRequest}.
 *
 * <p>{@code discountTier} and {@code origin} are carried as Strings and parsed by the domain, so
 * that an unknown tier answers with spec-04's 400 {@code ErrorResponse} rather than with Jackson's
 * bare 400 on a failed enum conversion.
 */
public record CreateMarkdownPlanRequestDto(String batchId,
                                           String sku,
                                           String discountTier,
                                           Instant effectiveFrom,
                                           Instant effectiveTo,
                                           String origin,
                                           String rationale,
                                           String agentTraceId) {
}
