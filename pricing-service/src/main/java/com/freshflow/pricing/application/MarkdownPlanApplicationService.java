package com.freshflow.pricing.application;

import com.freshflow.pricing.domain.BatchId;
import com.freshflow.pricing.domain.DiscountTier;
import com.freshflow.pricing.domain.DomainRuleViolation;
import com.freshflow.pricing.domain.MarginFloorAssessment;
import com.freshflow.pricing.domain.MarginFloorBreach;
import com.freshflow.pricing.domain.MarginFloorPolicy;
import com.freshflow.pricing.domain.MarkdownEventPublisher;
import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.MarkdownPlanId;
import com.freshflow.pricing.domain.Money;
import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.PromotionWindow;
import com.freshflow.pricing.domain.ProposalOrigin;
import com.freshflow.pricing.domain.Sku;
import com.freshflow.pricing.infrastructure.repository.MarkdownPlanRepository;
import com.freshflow.pricing.infrastructure.repository.PriceRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.Optional;

/**
 * Application service (spec-01 &sect;2.1) &mdash; FR9 "create and activate a markdown plan" and
 * FR11 "reject a markdown that breaches the margin floor", which spec-02 says is not a command of
 * its own but the invariant FR9's command must satisfy.
 *
 * <p>A separate class from {@link PriceApplicationService}: CLAUDE.md prefers one handler per
 * complex use case over a bloated application service, and proposing a markdown is a different use
 * case from reading or maintaining a price.
 *
 * <p>It orchestrates and decides nothing. Every rule it appears to apply belongs to something else:
 * <ul>
 *   <li><b>What the tier does to the price, and whether that breaches the floor</b> &mdash;
 *       {@link MarginFloorPolicy}, a domain service, because the rule spans {@code PriceRule} and
 *       {@code MarkdownPlan} (spec-03 &sect;3).</li>
 *   <li><b>Whether the plan may activate, and what a refusal does to it</b> &mdash; inside
 *       {@link MarkdownPlan}, the aggregate (spec-03 &sect;3 invariants 1, 3, 4).</li>
 *   <li><b>Whether the window is well formed</b> &mdash; {@link PromotionWindow} (invariant 2).</li>
 *   <li><b>Which names are tiers and origins</b> &mdash; the value objects' own parsers.</li>
 *   <li><b>Not recording the same recommendation twice</b> &mdash; the inbound adapter.</li>
 * </ul>
 * What remains here is request-shape validation ("the field is missing"), which spec-01 &sect;1.1
 * places in this layer, and the sequencing: load the rule, propose, assess, activate, save,
 * announce.
 *
 * <p><b>The refusal path is the Anti-Corruption Layer made visible</b> (spec-01 &sect;1.5). A
 * breach is caught here, not propagated: the plan the aggregate has already marked REJECTED is
 * saved so the refusal is queryable (FR11: "rejection is recorded and queryable"), the transaction
 * commits, and the caller receives the assessment's numbers as a typed outcome. Letting the
 * exception escape a {@code @Transactional} method would roll the rejected plan back and leave
 * nothing to query. Nothing is published on a refusal; nothing reaches the till.
 *
 * <p>{@code createdAt} and the event's {@code occurredAt} are stamped from the injected
 * {@link Clock}: producer-assigned event time (spec-01 &sect;2.6), never read inside the domain.
 */
@Service
public class MarkdownPlanApplicationService {

    /** The persisted column holds this many characters; the shape check and the adapter both read it. */
    public static final int RATIONALE_MAX_LENGTH = 2000;

    private final PriceRuleRepository priceRuleRepository;
    private final MarkdownPlanRepository markdownPlanRepository;
    private final MarginFloorPolicy marginFloorPolicy;
    private final MarkdownEventPublisher markdownEventPublisher;
    private final Clock clock;

    public MarkdownPlanApplicationService(PriceRuleRepository priceRuleRepository,
                                          MarkdownPlanRepository markdownPlanRepository,
                                          MarginFloorPolicy marginFloorPolicy,
                                          MarkdownEventPublisher markdownEventPublisher,
                                          Clock clock) {
        this.priceRuleRepository = priceRuleRepository;
        this.markdownPlanRepository = markdownPlanRepository;
        this.marginFloorPolicy = marginFloorPolicy;
        this.markdownEventPublisher = markdownEventPublisher;
        this.clock = clock;
    }

    /**
     * FR9 + FR11 &mdash; propose a markdown and have the domain activate or refuse it.
     *
     * <p>The order of answers follows spec-04: a malformed request is 400 before anything is looked
     * up; a SKU with no rule is 404, because there is no floor to check against; and only a
     * well-formed proposal against an existing rule reaches the domain's 201-or-422 decision.
     */
    @Transactional
    public MarkdownPlanOutcome create(CreateMarkdownPlanCommand command) {
        String shapeError = validateShape(command);
        if (shapeError != null) {
            return new MarkdownPlanOutcome.Invalid("VALIDATION_FAILED", shapeError);
        }

        Sku sku;
        BatchId batchId;
        DiscountTier tier;
        ProposalOrigin origin;
        PromotionWindow window;
        try {
            sku = Sku.of(command.sku());
            batchId = BatchId.of(command.batchId());
            tier = DiscountTier.fromName(command.discountTier());
            origin = ProposalOrigin.fromName(command.origin());
            window = PromotionWindow.of(command.effectiveFrom(), command.effectiveTo());
        } catch (DomainRuleViolation violation) {
            // The domain refused the proposal's shape -- an unknown tier, an inverted window. We
            // only carry the verdict to the edge.
            return new MarkdownPlanOutcome.Invalid(violation.code(), violation.getMessage());
        }

        Optional<PriceRule> rule = priceRuleRepository.findBySku(sku);
        if (rule.isEmpty()) {
            return new MarkdownPlanOutcome.PriceRuleNotFound(sku.value(), "PRICE_RULE_NOT_FOUND",
                    "No price rule exists for SKU " + sku.value()
                            + ", so there is no margin floor to check the proposal against.");
        }

        Instant now = clock.instant();
        MarkdownPlan plan = MarkdownPlan.propose(
                MarkdownPlanId.newId(), batchId, sku, tier, window, origin,
                blankToNull(command.rationale()), blankToNull(command.agentTraceId()), now);

        // The domain service computes; the aggregate decides. Neither knows who is asking.
        MarginFloorAssessment assessment = marginFloorPolicy.assess(rule.get(), tier);
        try {
            plan.activate(assessment);
        } catch (MarginFloorBreach breach) {
            // FR11: the rejection is recorded and queryable. The aggregate already holds the
            // reason; saving it is what makes the refusal an audit record rather than a log line.
            markdownPlanRepository.save(plan);
            return new MarkdownPlanOutcome.Rejected(toView(plan), toRejection(plan, breach.assessment()));
        }

        markdownPlanRepository.save(plan);
        // FR9: "Publishes markdown-applied on success". The event carries the price the domain
        // authorised; the adapter behind the port decides where it goes.
        markdownEventPublisher.publish(plan.toMarkdownAppliedEvent(now));
        return new MarkdownPlanOutcome.Activated(toView(plan));
    }

    /**
     * FR11's "queryable" &mdash; a plan by id, including a REJECTED one. An absent plan is not a
     * failure of the request, so it is an absent value; the controller maps it to 404.
     */
    @Transactional(readOnly = true)
    public Optional<MarkdownPlanView> findPlan(String markdownPlanId) {
        if (markdownPlanId == null || markdownPlanId.isBlank()) {
            return Optional.empty();
        }
        return markdownPlanRepository.findById(MarkdownPlanId.of(markdownPlanId)).map(this::toView);
    }

    /**
     * Request-shape validation only. Every rule here is about whether the request is well formed,
     * never about whether it is allowed. Returns null when the shape is acceptable.
     */
    private static String validateShape(CreateMarkdownPlanCommand command) {
        if (command == null) {
            return "Request body is required.";
        }
        if (command.batchId() == null || command.batchId().isBlank()) {
            return "Field 'batchId' is required.";
        }
        if (command.sku() == null || command.sku().isBlank()) {
            return "Field 'sku' is required.";
        }
        if (command.discountTier() == null || command.discountTier().isBlank()) {
            return "Field 'discountTier' is required.";
        }
        if (command.effectiveFrom() == null) {
            return "Field 'effectiveFrom' is required and must be an ISO-8601 timestamp.";
        }
        if (command.effectiveTo() == null) {
            return "Field 'effectiveTo' is required and must be an ISO-8601 timestamp.";
        }
        if (command.origin() == null || command.origin().isBlank()) {
            return "Field 'origin' is required.";
        }
        if (command.rationale() != null && command.rationale().length() > RATIONALE_MAX_LENGTH) {
            return "Field 'rationale' must be at most " + RATIONALE_MAX_LENGTH + " characters.";
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private MarkdownPlanView toView(MarkdownPlan plan) {
        Money discountedPrice = plan.discountedPrice();
        return new MarkdownPlanView(
                plan.markdownPlanId().value(),
                plan.batchId().value(),
                plan.sku().value(),
                plan.discountTier().name(),
                plan.window().effectiveFrom(),
                plan.window().effectiveTo(),
                plan.status().name(),
                plan.origin().name(),
                discountedPrice == null ? null : discountedPrice.amount(),
                discountedPrice == null ? null : discountedPrice.currency(),
                plan.rationale(),
                plan.agentTraceId(),
                plan.rejectionReason(),
                plan.createdAt());
    }

    private static MarginFloorRejectionView toRejection(MarkdownPlan plan, MarginFloorAssessment assessment) {
        return new MarginFloorRejectionView(
                plan.markdownPlanId().value(),
                plan.sku().value(),
                assessment.requestedTier().name(),
                assessment.resultingPrice().amount(),
                assessment.resultingPrice().currency(),
                assessment.unitCost().amount(),
                assessment.unitCost().currency(),
                assessment.resultingMarginRatio(),
                assessment.floor().ratio(),
                assessment.message());
    }
}
