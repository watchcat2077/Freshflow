package com.freshflow.sales.infrastructure;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for {@code SaleTransaction}.
 *
 * <p>spec-01 &sect;2.3's persistence-ignorance rule is written for services designated Domain Model,
 * but spec-01 &sect;2.2 forbids JPA imports in <i>any</i> domain package, so the same separation
 * holds here: the domain record stays annotation-free and this is its persistence counterpart.
 */
@Entity
@Table(name = "sale_transaction")
public class SaleTransactionJpaEntity {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "till_id", nullable = false)
    private String tillId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sale_line_item", joinColumns = @JoinColumn(name = "transaction_id"))
    private List<LineItemEmbeddable> lineItems = new ArrayList<>();

    protected SaleTransactionJpaEntity() {
        // required by JPA
    }

    public SaleTransactionJpaEntity(String transactionId, String tillId, Instant occurredAt,
                                    BigDecimal totalAmount, String currency,
                                    List<LineItemEmbeddable> lineItems) {
        this.transactionId = transactionId;
        this.tillId = tillId;
        this.occurredAt = occurredAt;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lineItems = lineItems;
    }
}
