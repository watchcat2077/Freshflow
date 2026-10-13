package com.freshflow.inventory.presentation;

import java.time.Instant;

/**
 * spec-04 {@code CreateBatchRequest}.
 *
 * <p>Carries no validation annotation. spec-01 &sect;1.1 lists no validation dependency and places
 * request validation in the application layer, so required-field and {@code minimum} checks are
 * hand-rolled there.
 */
public record CreateBatchRequestDto(String sku,
                                    Integer quantity,
                                    Instant expiryAt,
                                    MoneyDto unitCost,
                                    String supplierRef) {
}
