package com.freshflow.pricing.domain;

/**
 * Lifecycle of a {@link MarkdownPlan} (spec-03 &sect;3).
 *
 * <p>The transitions are the aggregate's business, not this enum's: PROPOSED &rarr; ACTIVE on a
 * passing margin assessment, PROPOSED &rarr; REJECTED on a breach or an explicit refusal, ACTIVE
 * &rarr; EXPIRED when the window closes (FR10, {@code [POST-MVP]}). REJECTED is terminal.
 */
public enum MarkdownStatus {

    PROPOSED,
    ACTIVE,
    EXPIRED,
    REJECTED
}
