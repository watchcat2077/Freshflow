package com.freshflow.pricing.application;

/**
 * Successful outcome of {@link PriceApplicationService#upsert}: the rule now in force, and whether
 * it was created or replaced &mdash; which spec-04 maps to 201 and 200 respectively. The controller
 * reads the flag; it does not work it out.
 */
public record UpsertPriceRuleResult(PriceView view, boolean created) {
}
