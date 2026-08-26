package com.freshflow.sales.presentation;

import java.time.Instant;
import java.util.List;

/**
 * spec-04 {@code CreateTransactionRequest}.
 *
 * <p>No validation annotation: spec-01 &sect;1.1 forbids {@code spring-boot-starter-validation}
 * pending the M-9 coordinator decision, so {@code minItems} and {@code minimum} are hand-rolled in
 * the application layer.
 */
public record CreateTransactionRequestDto(String tillId, Instant occurredAt, List<LineItemDto> lineItems) {

    public record LineItemDto(String sku, Integer quantity) {
    }
}
