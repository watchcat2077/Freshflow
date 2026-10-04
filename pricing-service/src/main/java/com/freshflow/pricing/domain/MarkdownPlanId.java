package com.freshflow.pricing.domain;

import java.util.Objects;
import java.util.UUID;

/** Value Object (spec-01 &sect;2.4) &mdash; identity of a {@link MarkdownPlan}. */
public record MarkdownPlanId(String value) {

    public MarkdownPlanId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new DomainRuleViolation("MARKDOWN_PLAN_ID_REQUIRED", "Markdown plan id must not be blank.");
        }
    }

    public static MarkdownPlanId of(String value) {
        return new MarkdownPlanId(value);
    }

    /**
     * A fresh identity. The {@code MDP-} prefix follows the {@code BAT-} / {@code TRC-} convention
     * spec-04 illustrates for this project's identifiers; the suffix only has to be unique.
     */
    public static MarkdownPlanId newId() {
        return new MarkdownPlanId("MDP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}
