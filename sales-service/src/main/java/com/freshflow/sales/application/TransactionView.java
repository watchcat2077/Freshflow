package com.freshflow.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read model returned by the application layer, so {@code presentation} handles no domain type. */
public record TransactionView(String transactionId,
                              String tillId,
                              Instant occurredAt,
                              BigDecimal totalAmount,
                              String currency,
                              List<Line> lineItems) {

    public record Line(String sku, int quantity, BigDecimal unitPrice, String currency,
                       boolean markdownApplied) {
    }
}
