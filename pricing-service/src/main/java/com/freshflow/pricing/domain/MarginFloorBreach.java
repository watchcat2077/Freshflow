package com.freshflow.pricing.domain;

import java.util.Objects;

/**
 * Raised by {@link MarkdownPlan#activate} when the margin floor would be breached (spec-03 &sect;3
 * invariant 1, FR11).
 *
 * <p>A {@link DomainRuleViolation} like any other refusal of this context, with the stable code
 * {@code MARGIN_FLOOR_BREACH}, plus the {@link MarginFloorAssessment} that caused it &mdash; so
 * the outer layers can report the numbers spec-04's {@code MarginFloorRejection} requires without
 * recomputing any of them (spec-01 &sect;2.5).
 *
 * <p>By the time this is thrown the plan has already recorded itself REJECTED with the
 * assessment's message. The exception says "no"; the aggregate's state says why, durably.
 */
public class MarginFloorBreach extends DomainRuleViolation {

    public static final String CODE = "MARGIN_FLOOR_BREACH";

    private final transient MarginFloorAssessment assessment;

    public MarginFloorBreach(MarginFloorAssessment assessment) {
        super(CODE, Objects.requireNonNull(assessment, "assessment").message());
        this.assessment = assessment;
    }

    public MarginFloorAssessment assessment() {
        return assessment;
    }
}
