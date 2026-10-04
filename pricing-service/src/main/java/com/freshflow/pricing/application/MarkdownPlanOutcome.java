package com.freshflow.pricing.application;

/**
 * Typed outcome of {@link MarkdownPlanApplicationService#create} &mdash; the four things spec-04
 * says {@code POST /api/markdown-plans} can answer, as four types.
 *
 * <p>A sealed interface rather than a status code or an exception, so that the controller can
 * {@code switch} over it exhaustively and perform a translation to 201, 422, 400 or 404 without
 * interpreting a value. spec-01 &sect;2.5: "In no case may an invariant live in a controller."
 * The consumer of {@code markdown-recommended} switches over the same type to decide what to log.
 */
public sealed interface MarkdownPlanOutcome {

    /** FR9: the floor held, the plan is ACTIVE and {@code markdown-applied} was published &rarr; 201. */
    record Activated(MarkdownPlanView plan) implements MarkdownPlanOutcome {
    }

    /**
     * FR11: the floor was breached. The plan is persisted as REJECTED and is queryable by the id the
     * rejection carries &rarr; 422 with spec-04's {@code MarginFloorRejection}.
     */
    record Rejected(MarkdownPlanView plan, MarginFloorRejectionView rejection) implements MarkdownPlanOutcome {
    }

    /** The request is not a proposal the domain can assess: missing field, unknown tier, inverted window &rarr; 400. */
    record Invalid(String code, String message) implements MarkdownPlanOutcome {
    }

    /** No price rule exists for the SKU, so there is no floor to check against &rarr; 404. */
    record PriceRuleNotFound(String sku, String code, String message) implements MarkdownPlanOutcome {
    }
}
