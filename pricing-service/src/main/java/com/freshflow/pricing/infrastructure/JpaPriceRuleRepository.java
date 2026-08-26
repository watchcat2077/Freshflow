package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.PriceRuleRepository;
import com.freshflow.pricing.domain.Sku;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter implementing the domain's {@code PriceRuleRepository} (spec-01 &sect;2.2). The application
 * layer is wired to the domain interface and never sees Spring Data.
 */
@Repository
public class JpaPriceRuleRepository implements PriceRuleRepository {

    private final PriceRuleJpaRepository jpaRepository;

    public JpaPriceRuleRepository(PriceRuleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(PriceRule priceRule) {
        jpaRepository.save(PriceRuleMapper.toEntity(priceRule));
    }

    @Override
    public Optional<PriceRule> findBySku(Sku sku) {
        return jpaRepository.findById(sku.value()).map(PriceRuleMapper::toDomain);
    }
}
