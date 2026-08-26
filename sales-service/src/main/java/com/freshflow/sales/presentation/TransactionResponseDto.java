package com.freshflow.sales.presentation;

import java.time.Instant;
import java.util.List;

/** spec-04 {@code TransactionResponse}. */
public record TransactionResponseDto(String transactionId,
                                     String tillId,
                                     Instant occurredAt,
                                     MoneyDto totalAmount,
                                     List<LineItemDto> lineItems) {

    public record LineItemDto(String sku, int quantity, MoneyDto unitPrice, boolean markdownApplied) {
    }
}
