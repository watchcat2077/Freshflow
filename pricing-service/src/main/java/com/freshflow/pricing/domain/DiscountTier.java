package com.freshflow.pricing.domain;

/**
 * Value Object (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; the discount tiers a markdown may be
 * expressed in, with what each one does to a price.
 *
 * <p>Four tiers, matching spec-04's {@code CreateMarkdownPlanRequest.discountTier} enumeration and
 * the {@code contracts} module's wire enum. {@code PERCENT_60} was added to spec-03 on 4 October
 * 2026 so that a breach can be demonstrated against the seeded rule: at base 4.50 and cost 2.10 it
 * yields 1.80, a margin of -0.1667, and is refused.
 *
 * <p>This is the Pricing context's own tier, re-declared here rather than imported from
 * {@code contracts} (spec-03 &sect;6: no shared kernel). The wire enum carries no arithmetic on
 * purpose; what a tier means for a price is this context's to decide, and {@link #percent()} is
 * where it decides it.
 */
public enum DiscountTier {

    PERCENT_20(20),
    PERCENT_30(30),
    PERCENT_50(50),
    PERCENT_60(60);

    private final int percent;

    DiscountTier(int percent) {
        this.percent = percent;
    }

    /** The percentage taken off the base price. */
    public int percent() {
        return percent;
    }

    /**
     * Parses a tier name arriving from outside the domain &mdash; a request body or an event
     * payload. Anything but the four names above is a rule violation, not a parse error: the set of
     * permissible tiers is a business decision of this context.
     */
    public static DiscountTier fromName(String name) {
        if (name != null) {
            for (DiscountTier tier : values()) {
                if (tier.name().equals(name)) {
                    return tier;
                }
            }
        }
        throw new DomainRuleViolation("UNKNOWN_TIER",
                "Unknown discount tier '" + name + "'. Expected one of PERCENT_20, PERCENT_30, "
                        + "PERCENT_50, PERCENT_60.");
    }
}
