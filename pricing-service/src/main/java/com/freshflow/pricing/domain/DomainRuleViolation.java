package com.freshflow.pricing.domain;

/**
 * Raised when a business invariant of the Pricing &amp; Markdown context would be breached.
 * Carries a stable machine-readable {@code code} so outer layers can report the outcome without
 * re-deciding it (spec-01 &sect;2.5).
 */
public class DomainRuleViolation extends RuntimeException {

    private final String code;

    public DomainRuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
