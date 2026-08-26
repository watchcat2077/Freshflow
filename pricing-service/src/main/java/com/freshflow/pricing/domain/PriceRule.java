package com.freshflow.pricing.domain;

import java.util.Objects;

/**
 * Entity (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; the cost and margin constraints for one SKU.
 * {@code sku} is its identity.
 *
 * <p>A POJO, persistence ignorant, no JPA annotation, importing nothing outside {@code java.*}
 * (spec-01 &sect;&sect;2.2 and 2.3). Its JPA counterpart and mapper live in {@code infrastructure}.
 */
public class PriceRule {

    private final Sku sku;
    private final Money basePrice;
    private final Money unitCost;
    private final String category;
    private final MarginFloor marginFloor;

    private PriceRule(Sku sku, Money basePrice, Money unitCost, String category, MarginFloor marginFloor) {
        this.sku = Objects.requireNonNull(sku, "sku");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
        this.unitCost = Objects.requireNonNull(unitCost, "unitCost");
        this.category = Objects.requireNonNull(category, "category");
        this.marginFloor = Objects.requireNonNull(marginFloor, "marginFloor");
    }

    public static PriceRule of(Sku sku, Money basePrice, Money unitCost, String category,
                               MarginFloor marginFloor) {
        if (!basePrice.currency().equals(unitCost.currency())) {
            throw new DomainRuleViolation("CURRENCY_MISMATCH",
                    "Base price and unit cost must be in the same currency.");
        }
        if (basePrice.amount().compareTo(unitCost.amount()) < 0) {
            throw new DomainRuleViolation("BASE_PRICE_BELOW_COST",
                    "Base price must not be below unit cost.");
        }
        return new PriceRule(sku, basePrice, unitCost, category, marginFloor);
    }

    /**
     * The price actually in force for this SKU right now.
     *
     * <p>Week 5 has no markdowns: FR9 and FR11 are {@code [MVP-PLUS]},
     * {@code POST /api/markdown-plans} is out of the current milestone, and spec-03 &sect;2 states
     * that where no markdown is in force the effective price equals the base price &mdash; "the
     * only case that exists in Week 5". When {@code MarkdownPlan} arrives in Week 7 this method is
     * where the active plan gets applied, which is why the caller asks the domain rather than
     * reading {@link #basePrice()} directly.
     */
    public Money effectivePrice() {
        return basePrice;
    }

    /** Null in Week 5, for the same reason {@link #effectivePrice()} returns the base price. */
    public String activeDiscountTier() {
        return null;
    }

    public Sku sku() {
        return sku;
    }

    public Money basePrice() {
        return basePrice;
    }

    public Money unitCost() {
        return unitCost;
    }

    public String category() {
        return category;
    }

    public MarginFloor marginFloor() {
        return marginFloor;
    }
}
