package com.freshflow.sales.domain;

import java.time.Instant;
import java.util.List;

/**
 * spec-03 &sect;2 &mdash; a recorded sale.
 *
 * <p>Explicitly NOT an aggregate root. spec-01 &sect;2.3 forbids aggregates in this service, so this
 * is a plain data structure that the transaction script assembles and hands on.
 */
public record SaleTransaction(String transactionId,
                              String tillId,
                              Instant occurredAt,
                              List<LineItem> lineItems,
                              Money totalAmount) {

    public SaleTransaction {
        lineItems = List.copyOf(lineItems);
    }
}
