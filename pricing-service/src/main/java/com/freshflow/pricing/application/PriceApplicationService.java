package com.freshflow.pricing.application;

import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.PriceRuleRepository;
import com.freshflow.pricing.domain.Sku;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Application service (spec-01 &sect;2.1) &mdash; the use cases of the Pricing &amp; Markdown
 * context that are in scope this week.
 *
 * <p>FR8 is {@code [MVP-CORE, minimal]}: "a read endpoint plus seeded configuration data is
 * sufficient. Full CRUD is [POST-MVP]." So this service reads, and nothing more. The markdown use
 * cases (FR9, FR11) are {@code [MVP-PLUS]} and are not implemented.
 *
 * <p>Note what it does <i>not</i> do: it does not compute the effective price. It asks the
 * {@link PriceRule} for it. That keeps the pricing rule inside the domain layer, where spec-01
 * &sect;2.5 requires this context's invariants to live.
 */
@Service
public class PriceApplicationService {

    private final PriceRuleRepository priceRuleRepository;

    public PriceApplicationService(PriceRuleRepository priceRuleRepository) {
        this.priceRuleRepository = priceRuleRepository;
    }

    /** FR8 &mdash; the effective price and margin floor for a SKU. */
    @Transactional(readOnly = true)
    public Optional<PriceView> findPrice(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return priceRuleRepository.findBySku(Sku.of(sku)).map(this::toView);
    }

    private PriceView toView(PriceRule rule) {
        return new PriceView(
                rule.sku().value(),
                rule.category(),
                rule.basePrice().amount(),
                rule.basePrice().currency(),
                rule.unitCost().amount(),
                rule.unitCost().currency(),
                rule.effectivePrice().amount(),
                rule.effectivePrice().currency(),
                rule.marginFloor().ratio(),
                rule.activeDiscountTier());
    }
}
