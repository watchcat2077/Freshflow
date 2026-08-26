package com.freshflow.sales.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code freshflow.sales.price-snapshot-seed[]}, the seeded configuration contract agreed in
 * spec-01 &sect;2.7 (Phase 3 finding A-1).
 *
 * <p>spec-03 &sect;2 fixes which fields the Week 5 static source supplies: sku, basePrice, unitCost,
 * category. This block is retired in Week 6, when the {@code price-rule-published} adapter replaces
 * the static one behind the unchanged port.
 */
@ConfigurationProperties(prefix = "freshflow.sales")
public class PriceSnapshotSeedProperties {

    private List<PriceSnapshotSeed> priceSnapshotSeed = new ArrayList<>();

    public List<PriceSnapshotSeed> getPriceSnapshotSeed() {
        return priceSnapshotSeed;
    }

    public void setPriceSnapshotSeed(List<PriceSnapshotSeed> priceSnapshotSeed) {
        this.priceSnapshotSeed = priceSnapshotSeed;
    }

    public static class PriceSnapshotSeed {

        private String sku;
        private BigDecimal basePrice;
        private BigDecimal unitCost;
        private String category;
        private String currency;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public BigDecimal getBasePrice() {
            return basePrice;
        }

        public void setBasePrice(BigDecimal basePrice) {
            this.basePrice = basePrice;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }
}
