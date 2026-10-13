package com.freshflow.sales.presentation;

import java.time.Instant;
import java.util.List;

/**
 * spec-04 {@code CreateTransactionRequest}.
 *
 * <p>No validation annotation: spec-01 &sect;1.1 lists no validation dependency and places request
 * validation in the application layer, so {@code minItems} and {@code minimum} are hand-rolled
 * there.
 */
public record CreateTransactionRequestDto(String tillId, Instant occurredAt, List<LineItemDto> lineItems) {

    public record LineItemDto(String sku, Integer quantity) {
    }
}
