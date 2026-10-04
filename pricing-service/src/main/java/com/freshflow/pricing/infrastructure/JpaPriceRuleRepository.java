package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.Sku;
import com.freshflow.pricing.infrastructure.repository.PriceRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter implementing {@code PriceRuleRepository}, the persistence port declared in
 * {@code infrastructure.repository} (spec-01 &sect;2.2 as amended by ADR 0005). The application
 * layer is wired to that interface, which speaks only the domain's language, and never sees
 * Spring Data.
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
