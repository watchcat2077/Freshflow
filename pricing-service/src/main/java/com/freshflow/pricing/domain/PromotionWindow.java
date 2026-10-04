package com.freshflow.pricing.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; when a markdown runs.
 *
 * <p>Carries spec-03 &sect;3 invariant 2 of {@code MarkdownPlan}: {@code effectiveTo} must be after
 * {@code effectiveFrom}. It is enforced in the constructor, so an inverted or empty window cannot
 * exist long enough to reach a plan.
 *
 * <p>The window is half-open: it contains its start and excludes its end. A markdown that runs
 * "until 22:00" is over at 22:00, and a scan at that instant is at the base price (FR6b: "a scan
 * after {@code effectiveTo} reverts to the base price without any further event").
 */
public record PromotionWindow(Instant effectiveFrom, Instant effectiveTo) {

    public PromotionWindow {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(effectiveTo, "effectiveTo");
        if (!effectiveTo.isAfter(effectiveFrom)) {
            throw new DomainRuleViolation("INVALID_WINDOW",
                    "effectiveTo must be after effectiveFrom.");
        }
    }

    public static PromotionWindow of(Instant effectiveFrom, Instant effectiveTo) {
        return new PromotionWindow(effectiveFrom, effectiveTo);
    }

    /** True from {@code effectiveFrom} inclusive to {@code effectiveTo} exclusive. */
    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(effectiveFrom) && instant.isBefore(effectiveTo);
    }
}
