package com.freshflow.inventory.presentation;

import java.time.Instant;

/**
 * spec-04 {@code CreateBatchRequest}.
 *
 * <p>Carries no validation annotation. spec-01 &sect;1.1 forbids
 * {@code spring-boot-starter-validation} pending the M-9 coordinator decision, so required-field
 * and {@code minimum} checks are hand-rolled in the application layer instead.
 */
public record CreateBatchRequestDto(String sku,
                                    Integer quantity,
                                    Instant expiryAt,
                                    MoneyDto unitCost,
                                    String supplierRef) {
}
