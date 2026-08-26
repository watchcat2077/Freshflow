package com.freshflow.inventory.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for {@code StockBatch}.
 *
 * <p>This class exists precisely so that the aggregate does not have to. spec-01 &sect;2.3: for a
 * service designated Domain Model, "domain classes are POJO, persistence ignorant. NO JPA
 * annotations on domain classes. A separate set of JPA entities plus mappers lives in
 * infrastructure."
 *
 * <p>Every field is a primitive or a JDBC-friendly type. {@code status} is stored as a String
 * rather than as the domain enum so that the persistence model carries no domain type at all, and
 * a rename in the domain cannot silently corrupt stored rows.
 */
@Entity
@Table(name = "stock_batch")
public class StockBatchJpaEntity {

    @Id
    @Column(name = "batch_id", nullable = false, updatable = false)
    private String batchId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "expiry_at", nullable = false)
    private Instant expiryAt;

    @Column(name = "unit_cost_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostAmount;

    @Column(name = "unit_cost_currency", nullable = false, length = 3)
    private String unitCostCurrency;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected StockBatchJpaEntity() {
        // required by JPA
    }

    public StockBatchJpaEntity(String batchId, String sku, int receivedQuantity, int remainingQuantity,
                               Instant expiryAt, BigDecimal unitCostAmount, String unitCostCurrency,
                               String status, Instant receivedAt) {
        this.batchId = batchId;
        this.sku = sku;
        this.receivedQuantity = receivedQuantity;
        this.remainingQuantity = remainingQuantity;
        this.expiryAt = expiryAt;
        this.unitCostAmount = unitCostAmount;
        this.unitCostCurrency = unitCostCurrency;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getSku() {
        return sku;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public Instant getExpiryAt() {
        return expiryAt;
    }

    public BigDecimal getUnitCostAmount() {
        return unitCostAmount;
    }

    public String getUnitCostCurrency() {
        return unitCostCurrency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
