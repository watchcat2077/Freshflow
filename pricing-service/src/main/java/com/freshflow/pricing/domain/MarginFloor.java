package com.freshflow.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; the minimum acceptable margin for a SKU,
 * as a ratio of the selling price.
 *
 * <p>Exposed as {@code marginFloorRatio} by {@code GET /api/prices/&#123;sku&#125;}, and consumed by
 * {@link MarginFloorPolicy}, whose {@link MarginFloorAssessment} compares a tier's resulting margin
 * against it (FR11). The range rule here is the one spec-04's {@code upsertPriceRule} surfaces as
 * 400.
 *
 * <p>Deliberately not normalised to a scale (spec-03 &sect;7): it is a ratio and not money, and a
 * floor such as {@code 0.125} must survive. Its rendering in the breach message is settled by
 * {@code MarginFloorAssessment.message()}.
 */
public record MarginFloor(BigDecimal ratio) {

    public MarginFloor {
        Objects.requireNonNull(ratio, "ratio");
        if (ratio.signum() < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainRuleViolation("MARGIN_FLOOR_OUT_OF_RANGE",
                    "Margin floor ratio must be between 0 and 1 inclusive.");
        }
    }

    public static MarginFloor of(BigDecimal ratio) {
        return new MarginFloor(ratio);
    }
}
