package com.freshflow.inventory.domain;

/**
 * Raised when a business invariant of this context would be breached.
 *
 * <p>Carries a stable machine-readable {@code code} so that the application layer can translate a
 * violation into spec-04's {@code ErrorResponse} without re-deciding anything and without parsing
 * a message string. The rule itself is decided here, in the domain (spec-01 &sect;2.5); the outer
 * layers only transport the outcome.
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
