package com.freshflow.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain Service (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; holds a discount tier against a price
 * rule and says what it would do to the margin.
 *
 * <p><b>This is the enforcement point of the Anti-Corruption Layer</b> (spec-01 &sect;1.5, FR11).
 * The rule spans {@code PriceRule}, which owns the price, cost and floor, and {@code MarkdownPlan},
 * which owns the tier and the decision to activate; it therefore belongs to neither aggregate and
 * is modelled as a service, exactly as spec-03 &sect;3 directs. It applies identically whether the
 * proposal originated from a human or from the LLM agent, because it never learns which.
 *
 * <p>Pure and stateless: a POJO importing nothing outside {@code java.*}, registered as a bean by
 * {@code infrastructure} rather than annotating itself (spec-01 &sect;2.2). It computes and decides
 * nothing about the plan; it reports, and {@link MarkdownPlan#activate} acts on the report.
 *
 * <p>The arithmetic, as ruled on 4 October 2026 (B-3, spec-03 &sect;3; spec-04
 * {@code createMarkdownPlan}): {@code resultingPrice = basePrice x (1 - tier/100)} half-up to cents,
 * which is {@link PriceRule#priceUnder} and lives there; {@code resultingMarginRatio =
 * (resultingPrice - unitCost) / resultingPrice} at scale 4, which lives here.
 */
public final class MarginFloorPolicy {

    public MarginFloorAssessment assess(PriceRule rule, DiscountTier tier) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(tier, "tier");

        Money resultingPrice = rule.priceUnder(tier);
        BigDecimal resultingMarginRatio = marginRatio(resultingPrice, rule.unitCost());

        return new MarginFloorAssessment(
                rule.basePrice(),
                rule.unitCost(),
                tier,
                resultingPrice,
                resultingMarginRatio,
                rule.marginFloor());
    }

    /**
     * Gross margin as a fraction of the selling price. A selling price of zero &mdash; reachable
     * only from a base price of zero, since no tier takes the whole price off &mdash; has no margin
     * to speak of and is reported as zero, which breaches any positive floor.
     */
    private static BigDecimal marginRatio(Money sellingPrice, Money unitCost) {
        BigDecimal price = sellingPrice.amount();
        if (price.signum() == 0) {
            return BigDecimal.ZERO.setScale(MarginFloorAssessment.RATIO_SCALE, Money.ROUNDING);
        }
        return price.subtract(unitCost.amount())
                .divide(price, MarginFloorAssessment.RATIO_SCALE, Money.ROUNDING);
    }
}
