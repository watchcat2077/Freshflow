package com.freshflow.inventory.presentation;

import java.time.Instant;

/** spec-04 {@code BatchResponse}. */
public record BatchResponseDto(String batchId,
                               String sku,
                               int receivedQuantity,
                               int remainingQuantity,
                               Instant expiryAt,
                               MoneyDto unitCost,
                               String status,
                               long remainingShelfLifeHours) {
}
