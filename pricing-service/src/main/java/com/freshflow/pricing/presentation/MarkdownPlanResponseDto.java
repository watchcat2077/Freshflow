package com.freshflow.pricing.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * spec-04 {@code MarkdownPlanResponse}.
 *
 * <p>Nulls are omitted, per the contract's own wording for {@code discountedPrice}: "Present when
 * the plan is ACTIVE; absent for a REJECTED plan." The same goes for {@code rejectionReason},
 * {@code agentTraceId} and {@code rationale}, each nullable in spec-04. The presentation layer may
 * import Jackson; the layers beneath it may not (spec-01 &sect;2.2), which is why the annotation is
 * here and not on the view it is built from.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarkdownPlanResponseDto(String markdownPlanId,
                                      String batchId,
                                      String sku,
                                      String discountTier,
                                      Instant effectiveFrom,
                                      Instant effectiveTo,
                                      String status,
                                      String origin,
                                      MoneyDto discountedPrice,
                                      String rationale,
                                      String agentTraceId,
                                      String rejectionReason,
                                      Instant createdAt) {
}
