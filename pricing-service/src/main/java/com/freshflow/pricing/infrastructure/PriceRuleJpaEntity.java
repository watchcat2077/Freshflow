package com.freshflow.pricing.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA entity for {@code PriceRule} (spec-01 &sect;2.3: separate JPA entities plus mappers live in
 * infrastructure, so the domain entity can stay persistence ignorant).
 */
@Entity
@Table(name = "price_rule")
public class PriceRuleJpaEntity {

    @Id
    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "base_price_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal basePriceAmount;

    @Column(name = "unit_cost_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "margin_floor_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal marginFloorRatio;

    protected PriceRuleJpaEntity() {
        // required by JPA
    }

    public PriceRuleJpaEntity(String sku, BigDecimal basePriceAmount, BigDecimal unitCostAmount,
                              String currency, String category, BigDecimal marginFloorRatio) {
        this.sku = sku;
        this.basePriceAmount = basePriceAmount;
        this.unitCostAmount = unitCostAmount;
        this.currency = currency;
        this.category = category;
        this.marginFloorRatio = marginFloorRatio;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getBasePriceAmount() {
        return basePriceAmount;
    }

    public BigDecimal getUnitCostAmount() {
        return unitCostAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getMarginFloorRatio() {
        return marginFloorRatio;
    }
}
