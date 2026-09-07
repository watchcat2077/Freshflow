package com.freshflow.insight.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * spec-04 {@code VelocityResponse}.
 *
 * <p>{@code baselineVelocity} and {@code velocityRatio} are omitted from the JSON when no baseline
 * is stored for the SKU, rather than serialised as zero. spec-04 marks no property of this schema
 * required, and a zero would assert a comparison that was never made.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VelocityResponseDto(String sku,
                                  Instant windowStart,
                                  Instant windowEnd,
                                  long quantitySold,
                                  double velocityPerHour,
                                  Double baselineVelocity,
                                  Double velocityRatio) {
}
