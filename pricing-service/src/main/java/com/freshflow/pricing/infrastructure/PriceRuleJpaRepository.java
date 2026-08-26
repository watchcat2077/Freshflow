package com.freshflow.pricing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@link PriceRuleJpaEntity}. An infrastructure detail. */
public interface PriceRuleJpaRepository extends JpaRepository<PriceRuleJpaEntity, String> {
}
