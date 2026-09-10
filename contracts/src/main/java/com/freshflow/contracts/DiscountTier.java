package com.freshflow.contracts;

/**
 * The discount tiers a markdown may be expressed in, on the wire.
 *
 * <p>Declared here rather than as a {@code String} because spec-04 fixes the set exactly &mdash;
 * {@code CreateMarkdownPlanRequest.discountTier} enumerates {@code PERCENT_20}, {@code PERCENT_30},
 * {@code PERCENT_50} and {@code PERCENT_60} and admits nothing else. A {@code String} field would
 * let a producer put {@code "PERCENT_45"} or {@code "half price"} onto {@code markdown-recommended}
 * and leave the consumer to discover it at run time; an enum makes the contract the compiler's
 * business. That matters more than usual here, because the producer of that topic is an agent
 * driven by a language model (FR14) and the consumer enforces a business invariant on what it
 * receives (FR11).
 *
 * <p>Not a domain {@code DiscountTier}. spec-01 &sect;2.4 lists {@code DiscountTier} as a value
 * object of the Pricing context, and spec-03 &sect;6 states there is deliberately no shared kernel:
 * each context re-declares its own. This type exists only so the Published Language (spec-01
 * &sect;1.5) can carry a tier across a context boundary, and carries no behaviour &mdash; in
 * particular, no percentage arithmetic and no notion of what a tier does to a price. What a tier
 * means for a price, and whether a given tier is permissible, are Pricing's to decide.
 */
public enum DiscountTier {

    PERCENT_20,
    PERCENT_30,
    PERCENT_50,
    PERCENT_60
}
