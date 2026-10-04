package com.freshflow.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object (spec-03 &sect;3, team ruling B-3 of 4 October 2026) &mdash; what
 * {@link MarginFloorPolicy} found when it held one discount tier against one price rule.
 *
 * <p>B-3 replaced the policy's original {@code boolean} with this type so that the numbers spec-04's
 * {@code MarginFloorRejection} reports &mdash; {@code resultingPrice}, {@code resultingMarginRatio},
 * {@code marginFloorRatio} &mdash; are the numbers the domain computed, carried outward, and never
 * recomputed by a layer that is not allowed to hold the rule (spec-01 &sect;2.5).
 *
 * <p>{@code resultingMarginRatio} is gross margin as a fraction of the selling price, the usual
 * retail definition, at scale 4 HALF_UP. It is a ratio and not money, so spec-03 &sect;7's monetary
 * convention does not apply to it; the scale is fixed here instead, on construction, for the same
 * reason {@code Money} fixes its own.
 *
 * @param basePrice            the rule's base price
 * @param unitCost             the rule's unit cost
 * @param requestedTier        the tier that was assessed
 * @param resultingPrice       {@code basePrice x (1 - tier/100)}, half-up to cents
 * @param resultingMarginRatio {@code (resultingPrice - unitCost) / resultingPrice}, scale 4
 * @param floor                the rule's margin floor
 */
public record MarginFloorAssessment(Money basePrice,
                                    Money unitCost,
                                    DiscountTier requestedTier,
                                    Money resultingPrice,
                                    BigDecimal resultingMarginRatio,
                                    MarginFloor floor) {

    /** Decimal places of {@code resultingMarginRatio}. */
    public static final int RATIO_SCALE = 4;

    public MarginFloorAssessment {
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(requestedTier, "requestedTier");
        Objects.requireNonNull(resultingPrice, "resultingPrice");
        Objects.requireNonNull(resultingMarginRatio, "resultingMarginRatio");
        Objects.requireNonNull(floor, "floor");
        resultingMarginRatio = resultingMarginRatio.setScale(RATIO_SCALE, Money.ROUNDING);
    }

    /** spec-03 &sect;3 invariant 1, as a question: is the resulting margin below the floor? */
    public boolean breached() {
        return resultingMarginRatio.compareTo(floor.ratio()) < 0;
    }

    /**
     * The sentence spec-03 &sect;3 and spec-04 illustrate: {@code "Discount tier PERCENT_60 yields
     * margin -0.1667, below floor 0.15"}. On a breach it becomes the plan's {@code rejectionReason}
     * and the 422 body's {@code message}, so the two are one string.
     *
     * <p>The floor is rendered without trailing zeros. It comes back from its {@code scale 4}
     * column as {@code 0.1500}, and spec-03 &sect;7 keeps {@code MarginFloor} out of the monetary
     * normalisation, so the rendering is settled here rather than in the value object.
     */
    public String message() {
        return "Discount tier " + requestedTier.name()
                + " yields margin " + resultingMarginRatio.toPlainString()
                + ", " + (breached() ? "below" : "at or above")
                + " floor " + floor.ratio().stripTrailingZeros().toPlainString();
    }
}
