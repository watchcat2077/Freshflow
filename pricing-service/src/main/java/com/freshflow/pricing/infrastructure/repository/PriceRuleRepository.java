package com.freshflow.pricing.infrastructure.repository;

import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.Sku;

import java.util.Optional;

/**
 * Repository interface declared in {@code domain} (spec-01 &sect;2.2). The JPA implementation lives
 * in {@code infrastructure}.
 *
 * <p>spec-03 &sect;3 names {@code MarkdownPlanRepository} as this context's other repository. It is
 * deliberately absent: {@code MarkdownPlan} is Week 7 work and spec-03 forbids implementing
 * {@code activate(...)} until findings B-3 and B-6 are settled.
 */
public interface PriceRuleRepository {

    void save(PriceRule priceRule);

    Optional<PriceRule> findBySku(Sku sku);
}
