package com.freshflow.pricing.infrastructure.repository;

import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.Sku;

import java.util.Optional;

/**
 * Repository interface for {@code PriceRule} (spec-03 &sect;3), declared in
 * {@code infrastructure.repository} per ADR 0005. The JPA
 * implementation lives beside it in {@code infrastructure}.
 *
 * <p>This context's other repository, {@link MarkdownPlanRepository}, sits next to it.
 */
public interface PriceRuleRepository {

    void save(PriceRule priceRule);

    Optional<PriceRule> findBySku(Sku sku);
}
