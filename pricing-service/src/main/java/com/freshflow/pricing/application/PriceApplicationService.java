package com.freshflow.pricing.application;

import com.freshflow.pricing.domain.DomainRuleViolation;
import com.freshflow.pricing.domain.MarginFloor;
import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.Money;
import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.Sku;
import com.freshflow.pricing.infrastructure.repository.MarkdownPlanRepository;
import com.freshflow.pricing.infrastructure.repository.PriceRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * Application service (spec-01 &sect;2.1) &mdash; FR8, the price rules and margin floors of the
 * Pricing &amp; Markdown context: read one, and create or replace one.
 *
 * <p>Depends inward on {@code domain}, and on the repository interfaces of
 * {@code infrastructure.repository} (ADR 0005). It orchestrates: it retrieves domain objects,
 * delegates the decisions to them, and saves the result. Every rule below either belongs to the
 * domain &mdash; base price not below cost, floor inside [0, 1], the same currency on both amounts
 * &mdash; or is request-shape validation, which spec-01 &sect;1.1 places here.
 *
 * <p><b>The effective price.</b> FR8's extended criterion (4 October 2026): "when a markdown plan is
 * ACTIVE inside its window, the discounted {@code effectivePrice} and the
 * {@code activeDiscountTier}"; otherwise the base price, and no tier (spec-03 &sect;2). Whether a
 * plan is in force is the plan's own answer ({@code MarkdownPlan.isInForceAt}), and the price it
 * carries is the one the domain authorised on activation; this layer only looks up the ACTIVE
 * plans and asks. Where two plans for one SKU are both in force &mdash; two batches of the same
 * product, both marked down &mdash; the deeper discount is the one shown, since that is the lowest
 * price a till has been told to charge.
 */
@Service
public class PriceApplicationService {

    private final PriceRuleRepository priceRuleRepository;
    private final MarkdownPlanRepository markdownPlanRepository;
    private final PriceRulePublicationService priceRulePublicationService;
    private final Clock clock;

    public PriceApplicationService(PriceRuleRepository priceRuleRepository,
                                   MarkdownPlanRepository markdownPlanRepository,
                                   PriceRulePublicationService priceRulePublicationService,
                                   Clock clock) {
        this.priceRuleRepository = priceRuleRepository;
        this.markdownPlanRepository = markdownPlanRepository;
        this.priceRulePublicationService = priceRulePublicationService;
        this.clock = clock;
    }

    /** FR8 &mdash; the effective price and margin floor for a SKU. */
    @Transactional(readOnly = true)
    public Optional<PriceView> findPrice(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        Sku key = Sku.of(sku);
        Instant now = clock.instant();
        return priceRuleRepository.findBySku(key).map(rule -> toView(rule, planInForce(key, now)));
    }

    /**
     * FR8 &mdash; create or replace the rule for a SKU, and announce it (spec-04
     * {@code upsertPriceRule}).
     *
     * <p>The announcement goes through {@link PriceRulePublicationService}, the use case that
     * already makes this service the producer of {@code price-rule-published}; it runs inside this
     * transaction, so a rule that is not committed is not announced. spec-01 &sect;1.3: the topic
     * is the only channel by which a price reaches {@code sales-service}.
     */
    @Transactional
    public ApplicationResult<UpsertPriceRuleResult> upsert(UpsertPriceRuleCommand command) {
        String shapeError = validateShape(command);
        if (shapeError != null) {
            return ApplicationResult.failure("VALIDATION_FAILED", shapeError);
        }

        try {
            Sku sku = Sku.of(command.sku());
            PriceRule rule = PriceRule.of(
                    sku,
                    Money.of(command.basePriceAmount(), command.basePriceCurrency()),
                    Money.of(command.unitCostAmount(), command.unitCostCurrency()),
                    command.category(),
                    MarginFloor.of(command.marginFloorRatio()));

            boolean created = priceRuleRepository.findBySku(sku).isEmpty();
            priceRuleRepository.save(rule);
            priceRulePublicationService.announce(sku.value());

            return ApplicationResult.success(
                    new UpsertPriceRuleResult(toView(rule, planInForce(sku, clock.instant())), created));
        } catch (DomainRuleViolation violation) {
            // The domain decided this, not us. We only carry the verdict to the edge.
            return ApplicationResult.failure(violation.code(), violation.getMessage());
        }
    }

    /** The ACTIVE plan whose window contains {@code now}, if any; the deepest discount when several do. */
    private Optional<MarkdownPlan> planInForce(Sku sku, Instant now) {
        return markdownPlanRepository.findActiveBySku(sku).stream()
                .filter(plan -> plan.isInForceAt(now))
                .min(Comparator.comparing(plan -> plan.discountedPrice().amount()));
    }

    /**
     * Request-shape validation only. Every rule here is about whether the request is well formed,
     * never about whether it is allowed. Returns null when the shape is acceptable.
     */
    private static String validateShape(UpsertPriceRuleCommand command) {
        if (command == null) {
            return "Request body is required.";
        }
        if (command.sku() == null || command.sku().isBlank()) {
            return "Path variable 'sku' is required.";
        }
        if (command.basePriceAmount() == null) {
            return "Field 'basePrice.amount' is required.";
        }
        if (command.basePriceCurrency() == null || command.basePriceCurrency().isBlank()) {
            return "Field 'basePrice.currency' is required.";
        }
        if (command.unitCostAmount() == null) {
            return "Field 'unitCost.amount' is required.";
        }
        if (command.unitCostCurrency() == null || command.unitCostCurrency().isBlank()) {
            return "Field 'unitCost.currency' is required.";
        }
        if (command.category() == null || command.category().isBlank()) {
            return "Field 'category' is required.";
        }
        if (command.marginFloorRatio() == null) {
            return "Field 'marginFloorRatio' is required.";
        }
        return null;
    }

    private static PriceView toView(PriceRule rule, Optional<MarkdownPlan> planInForce) {
        Money effectivePrice = planInForce.map(MarkdownPlan::discountedPrice).orElse(rule.basePrice());
        String activeDiscountTier = planInForce.map(plan -> plan.discountTier().name()).orElse(null);
        return new PriceView(
                rule.sku().value(),
                rule.category(),
                rule.basePrice().amount(),
                rule.basePrice().currency(),
                rule.unitCost().amount(),
                rule.unitCost().currency(),
                effectivePrice.amount(),
                effectivePrice.currency(),
                rule.marginFloor().ratio(),
                activeDiscountTier);
    }
}
