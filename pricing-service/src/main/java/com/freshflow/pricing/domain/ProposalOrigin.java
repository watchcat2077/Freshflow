package com.freshflow.pricing.domain;

/**
 * Who proposed a markdown (spec-03 &sect;3): a person through {@code POST /api/markdown-plans}, or
 * FR14's agent through {@code markdown-recommended}.
 *
 * <p>Recorded for the audit trail and for nothing else. No rule in this context reads it: the
 * margin floor applies identically to both (FR11, spec-01 &sect;2.5), and a reader who finds a
 * branch on this enum anywhere in {@code domain} has found a defect.
 */
public enum ProposalOrigin {

    HUMAN,
    AGENT;

    /** Parses an origin arriving from outside the domain. */
    public static ProposalOrigin fromName(String name) {
        if (name != null) {
            for (ProposalOrigin origin : values()) {
                if (origin.name().equals(name)) {
                    return origin;
                }
            }
        }
        throw new DomainRuleViolation("UNKNOWN_ORIGIN",
                "Unknown proposal origin '" + name + "'. Expected HUMAN or AGENT.");
    }
}
