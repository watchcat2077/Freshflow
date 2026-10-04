package com.freshflow.pricing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Entity (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; the cost and margin constraints for one SKU.
 * {@code sku} is its identity.
 *
 * <p>A POJO, persistence ignorant, no JPA annotation, importing nothing outside {@code java.*}
 * (spec-01 &sect;&sect;2.2 and 2.3). Its JPA counterpart and mapper live in {@code infrastructure}.
 *
 * <p>It holds no reference to any {@code MarkdownPlan}. What a tier does to this rule's price is
 * this entity's arithmetic ({@link #priceUnder}); whether that price clears the floor is
 * {@link MarginFloorPolicy}'s assessment; and which plan, if any, is in force right now is a
 * question for the application layer, which has the clock and the repository. Keeping the three
 * apart is what lets the domain state the rule once and never ask what time it is.
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
     * The price this SKU would sell at under a discount tier: {@code basePrice x (1 - tier/100)},
     * rounded half-up to cents (spec-04 {@code createMarkdownPlan}, spec-03 &sect;7).
     *
     * <p>The one place in the system where a tier becomes a price. {@link MarginFloorPolicy} reads
     * it to assess the margin, {@code MarkdownPlan} records the assessed price on activation, and
     * the {@code markdown-applied} event carries that price so that no consumer recomputes it.
     * The rounding is {@link Money}'s own, applied on construction.
     */
    public Money priceUnder(DiscountTier tier) {
        Objects.requireNonNull(tier, "tier");
        BigDecimal retainedFraction = BigDecimal.valueOf(100 - tier.percent()).movePointLeft(2);
        return Money.of(basePrice.amount().multiply(retainedFraction), basePrice.currency());
    }

    /**
     * The domain event announcing this rule, for the {@code price-rule-published} topic.
     *
     * <p>spec-01 &sect;2.6 fixes the key fields for that topic as sku, basePrice, unitCost and
     * category &mdash; the base price, deliberately, never a discounted one. The topic carries the
     * standing rule; a markdown in force is announced separately on {@code markdown-applied}.
     *
     * <p>The instant is passed in rather than read from a clock here, because the domain never
     * asks what time it is.
     */
    public PriceRulePublished toPriceRulePublishedEvent(Instant occurredAt) {
        return new PriceRulePublished(sku, basePrice, unitCost, category, occurredAt);
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
