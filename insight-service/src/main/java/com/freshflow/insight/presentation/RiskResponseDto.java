package com.freshflow.insight.presentation;

import java.time.Instant;

/**
 * spec-04 {@code RiskResponse}, in its field order.
 *
 * <p>{@code atRisk} is deliberately absent. Every element of this response is by definition
 * currently flagged &mdash; spec-04 calls the endpoint "batches currently flagged as at risk"
 * &mdash; so serialising the flag would restate what the list itself already asserts. It exists on
 * the read model because the topology assesses safe batches too; it has no place on the wire.
 */
public record RiskResponseDto(String batchId,
                              String sku,
                              int remainingQuantity,
                              long hoursToExpiry,
                              double currentVelocity,
                              double projectedSales,
                              double projectedShortfall,
                              Instant detectedAt) {
}
