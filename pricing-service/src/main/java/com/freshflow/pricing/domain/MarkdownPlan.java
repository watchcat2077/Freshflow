package com.freshflow.pricing.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate Root (spec-01 &sect;2.4, spec-03 &sect;3) &mdash; a proposal to mark one batch down by
 * one tier for one window, and what became of it.
 *
 * <p>A Plain Old Java Object, persistence ignorant, carrying no JPA annotation and importing
 * nothing outside {@code java.*} (spec-01 &sect;&sect;2.2 and 2.3). Its JPA counterpart and the
 * mapper between them live in {@code infrastructure}.
 *
 * <p><b>Invariants enforced here and nowhere else</b> (spec-01 &sect;2.5, spec-03 &sect;3):
 * <ol>
 *   <li>A plan may only be activated if the discounted price is at or above the margin floor.</li>
 *   <li>{@code effectiveTo} must be after {@code effectiveFrom} &mdash; carried by
 *       {@link PromotionWindow}, so an invalid window cannot reach a plan.</li>
 *   <li>Only a PROPOSED plan may be activated. Only an ACTIVE plan may expire.</li>
 *   <li>A REJECTED plan is terminal and cannot be reactivated.</li>
 *   <li>{@code origin} is immutable after creation: a final field and no method that takes one.</li>
 * </ol>
 *
 * <p><b>Invariant 1 is the rule behind the Anti-Corruption Layer</b> (spec-01 &sect;1.5, FR11).
 * The layer itself is the translation in this context's inbound adapter,
 * {@code MarkdownRecommendedMessageHandler}: the agent's integration event becomes this context's
 * command and types. The margin floor is then applied behind that translation to human and agent
 * proposals alike. How: {@link #activate} receives the {@link MarginFloorAssessment} that
 * {@link MarginFloorPolicy} produced for this plan's tier against the SKU's {@code PriceRule}. The
 * aggregate decides from those numbers, never holds a {@code PriceRule} of its own, and cannot
 * tell &mdash; because nothing here reads {@link #origin()} &mdash; whether a human or the agent is
 * asking. On a breach it records itself REJECTED with the assessment's message and throws
 * {@link MarginFloorBreach}; the application layer persists the rejected plan so the refusal is
 * queryable.
 */
public class MarkdownPlan {

    private final MarkdownPlanId markdownPlanId;
    private final BatchId batchId;
    private final Sku sku;
    private final DiscountTier discountTier;
    private final PromotionWindow window;
    private final ProposalOrigin origin;
    private final String rationale;
    private final String agentTraceId;
    private final Instant createdAt;

    private MarkdownStatus status;
    private String rejectionReason;
    private Money discountedPrice;

    private MarkdownPlan(MarkdownPlanId markdownPlanId, BatchId batchId, Sku sku, DiscountTier discountTier,
                         PromotionWindow window, MarkdownStatus status, ProposalOrigin origin,
                         String rationale, String agentTraceId, String rejectionReason,
                         Money discountedPrice, Instant createdAt) {
        this.markdownPlanId = Objects.requireNonNull(markdownPlanId, "markdownPlanId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.discountTier = Objects.requireNonNull(discountTier, "discountTier");
        this.window = Objects.requireNonNull(window, "window");
        this.status = Objects.requireNonNull(status, "status");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.rationale = rationale;
        this.agentTraceId = agentTraceId;
        this.rejectionReason = rejectionReason;
        this.discountedPrice = discountedPrice;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Proposes a markdown (FR9, and FR14's {@code markdown-recommended} on arrival). Every plan
     * begins PROPOSED; the only way to become ACTIVE is {@link #activate}, which is what makes
     * invariant 1 unavoidable rather than merely documented.
     *
     * @param rationale    FR14's written justification, or a supervisor's; may be null
     * @param agentTraceId the key to the agent's tool-call trace; null for a human proposal
     * @param createdAt    producer-assigned time, handed in because the domain never asks the clock
     */
    public static MarkdownPlan propose(MarkdownPlanId markdownPlanId, BatchId batchId, Sku sku,
                                       DiscountTier discountTier, PromotionWindow window,
                                       ProposalOrigin origin, String rationale, String agentTraceId,
                                       Instant createdAt) {
        return new MarkdownPlan(markdownPlanId, batchId, sku, discountTier, window,
                MarkdownStatus.PROPOSED, origin, rationale, agentTraceId, null, null, createdAt);
    }

    /**
     * Rebuilds a plan from storage. Deliberately bypasses {@link #propose}: a persisted plan already
     * went through its transitions, and replaying them here would re-decide what was decided.
     */
    public static MarkdownPlan rehydrate(MarkdownPlanId markdownPlanId, BatchId batchId, Sku sku,
                                         DiscountTier discountTier, PromotionWindow window,
                                         MarkdownStatus status, ProposalOrigin origin, String rationale,
                                         String agentTraceId, String rejectionReason,
                                         Money discountedPrice, Instant createdAt) {
        return new MarkdownPlan(markdownPlanId, batchId, sku, discountTier, window, status, origin,
                rationale, agentTraceId, rejectionReason, discountedPrice, createdAt);
    }

    /**
     * FR9 &mdash; activates the plan, or refuses it (FR11).
     *
     * <p>Invariant 4 is checked first, then 3, then the assessment is checked to be about this
     * plan's own tier, and only then is invariant 1 evaluated. The order matters: a REJECTED plan
     * answers "terminal" even when handed a passing assessment.
     *
     * @throws MarginFloorBreach    if the assessment is breached; the plan is now REJECTED
     * @throws DomainRuleViolation  if the plan is not PROPOSED, or the assessment is for another tier
     */
    public void activate(MarginFloorAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        requireNotRejected("activated");
        if (status != MarkdownStatus.PROPOSED) {
            throw new DomainRuleViolation("PLAN_NOT_PROPOSED",
                    "Only a PROPOSED plan may be activated; this plan is " + status + ".");
        }
        if (assessment.requestedTier() != discountTier) {
            throw new DomainRuleViolation("ASSESSMENT_TIER_MISMATCH",
                    "The assessment is for " + assessment.requestedTier()
                            + " but this plan proposes " + discountTier + ".");
        }

        // Invariant 1. The refusal is recorded before it is thrown, so that what the domain
        // decided survives as state and not only as an exception somebody may catch.
        if (assessment.breached()) {
            status = MarkdownStatus.REJECTED;
            rejectionReason = assessment.message();
            throw new MarginFloorBreach(assessment);
        }

        status = MarkdownStatus.ACTIVE;
        discountedPrice = assessment.resultingPrice();
    }

    /**
     * FR10 &mdash; the window closed. {@code [POST-MVP]}; the rule is stated here because it is an
     * invariant of this aggregate (3), not because the use case is wired up.
     */
    public void expire() {
        requireNotRejected("expired");
        if (status != MarkdownStatus.ACTIVE) {
            throw new DomainRuleViolation("PLAN_NOT_ACTIVE",
                    "Only an ACTIVE plan may expire; this plan is " + status + ".");
        }
        status = MarkdownStatus.EXPIRED;
    }

    /**
     * Refuses a PROPOSED plan for a reason other than the margin floor. A reason is required: a
     * rejection with nothing to say would not be the queryable record FR11 asks for.
     */
    public void reject(String reason) {
        requireNotRejected("rejected");
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolation("REJECTION_REASON_REQUIRED",
                    "A rejection must carry a reason.");
        }
        if (status != MarkdownStatus.PROPOSED) {
            throw new DomainRuleViolation("PLAN_NOT_PROPOSED",
                    "Only a PROPOSED plan may be rejected; this plan is " + status + ".");
        }
        status = MarkdownStatus.REJECTED;
        rejectionReason = reason;
    }

    /** Whether this plan is the markdown in force at {@code now}: ACTIVE, and inside its window. */
    public boolean isInForceAt(Instant now) {
        return status == MarkdownStatus.ACTIVE && window.contains(now);
    }

    /**
     * The domain event announcing the activation, for the {@code markdown-applied} topic. It carries
     * the price the domain authorised so that no consumer has to recompute it; the instant is handed
     * in because the domain never asks the clock.
     */
    public MarkdownApplied toMarkdownAppliedEvent(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (status != MarkdownStatus.ACTIVE) {
            throw new DomainRuleViolation("PLAN_NOT_ACTIVE",
                    "Only an ACTIVE plan has a markdown-applied event; this plan is " + status + ".");
        }
        return new MarkdownApplied(markdownPlanId, batchId, sku, discountTier, window, discountedPrice,
                occurredAt);
    }

    /** Invariant 4: once REJECTED, nothing further happens to a plan. */
    private void requireNotRejected(String attempted) {
        if (status == MarkdownStatus.REJECTED) {
            throw new DomainRuleViolation("PLAN_REJECTED",
                    "A rejected plan is terminal and cannot be " + attempted + ".");
        }
    }

    public MarkdownPlanId markdownPlanId() {
        return markdownPlanId;
    }

    public BatchId batchId() {
        return batchId;
    }

    public Sku sku() {
        return sku;
    }

    public DiscountTier discountTier() {
        return discountTier;
    }

    public PromotionWindow window() {
        return window;
    }

    public MarkdownStatus status() {
        return status;
    }

    public ProposalOrigin origin() {
        return origin;
    }

    /** Null when the proposer gave none. */
    public String rationale() {
        return rationale;
    }

    /** Null for a human proposal (spec-03 &sect;3: "set when origin is AGENT"). */
    public String agentTraceId() {
        return agentTraceId;
    }

    /** Null unless the plan is REJECTED. */
    public String rejectionReason() {
        return rejectionReason;
    }

    /** Set on activation from the assessment (spec-03 &sect;3); null before that and for a rejected plan. */
    public Money discountedPrice() {
        return discountedPrice;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
