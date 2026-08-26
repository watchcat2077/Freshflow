package com.freshflow.sales.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/** Persistence form of {@code LineItem}, stored as an element collection of its transaction. */
@Embeddable
public class LineItemEmbeddable {

    @Column(name = "line_item_id", nullable = false)
    private String lineItemId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false, length = 3)
    private String unitPriceCurrency;

    @Column(name = "markdown_applied", nullable = false)
    private boolean markdownApplied;

    protected LineItemEmbeddable() {
        // required by JPA
    }

    public LineItemEmbeddable(String lineItemId, String sku, int quantity, BigDecimal unitPriceAmount,
                              String unitPriceCurrency, boolean markdownApplied) {
        this.lineItemId = lineItemId;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPriceAmount = unitPriceAmount;
        this.unitPriceCurrency = unitPriceCurrency;
        this.markdownApplied = markdownApplied;
    }
}
