package com.freshflow.pricing.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code freshflow.pricing.price-rules[]}, the seeded configuration contract recorded in
 * spec-01 &sect;2.7.
 *
 * <p>FR8 is {@code [MVP-CORE, minimal]}: "a read endpoint plus seeded configuration data is
 * sufficient. Full CRUD is [POST-MVP]." The key names here are the ones spec-01 &sect;2.7 fixes;
 * changing either these or the test resources requires changing both together.
 */
@ConfigurationProperties(prefix = "freshflow.pricing")
public class PriceRuleSeedProperties {

    private List<PriceRuleSeed> priceRules = new ArrayList<>();

    public List<PriceRuleSeed> getPriceRules() {
        return priceRules;
    }

    public void setPriceRules(List<PriceRuleSeed> priceRules) {
        this.priceRules = priceRules;
    }

    public static class PriceRuleSeed {

        private String sku;
        private BigDecimal basePrice;
        private BigDecimal unitCost;
        private String category;
        private String currency;
        private BigDecimal marginFloorRatio;

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

        public BigDecimal getMarginFloorRatio() {
            return marginFloorRatio;
        }

        public void setMarginFloorRatio(BigDecimal marginFloorRatio) {
            this.marginFloorRatio = marginFloorRatio;
        }
    }
}
