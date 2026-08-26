package com.freshflow.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; the minimum acceptable margin for a SKU,
 * as a ratio.
 *
 * <p>Exposed as {@code marginFloorRatio} by {@code GET /api/prices/&#123;sku&#125;}. The rule that
 * consumes it &mdash; {@code MarginFloorPolicy}, and the 422 rejection path &mdash; is Week 7 and
 * is deliberately not implemented: spec-03 &sect;3 records findings B-3 and B-6 as open and says
 * "Do not implement the 422 path until this is settled."
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
