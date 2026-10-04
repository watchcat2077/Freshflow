package com.freshflow.sales.domain;

/**
 * spec-03 &sect;2 &mdash; one scanned line on a transaction.
 *
 * <p>A plain data structure, deliberately without behaviour: spec-01 &sect;2.3 designates this
 * service Transaction Script, so the logic that decides {@code unitPrice} and
 * {@code markdownApplied} lives in the application-layer script, not here. The script obtains
 * both by asking {@code PriceSnapshot#priceAt} for the price in force at the transaction's
 * {@code occurredAt} (FR6a, FR6b); this record only carries the answer.
 */
public record LineItem(String lineItemId,
                       Sku sku,
                       int quantity,
                       Money unitPrice,
                       boolean markdownApplied) {

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
