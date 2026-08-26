package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.MarginFloor;
import com.freshflow.pricing.domain.Money;
import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.Sku;

/** Translates between the persistence-ignorant {@link PriceRule} and its JPA entity. */
final class PriceRuleMapper {

    private PriceRuleMapper() {
    }

    static PriceRuleJpaEntity toEntity(PriceRule rule) {
        return new PriceRuleJpaEntity(
                rule.sku().value(),
                rule.basePrice().amount(),
                rule.unitCost().amount(),
                rule.basePrice().currency(),
                rule.category(),
                rule.marginFloor().ratio());
    }

    static PriceRule toDomain(PriceRuleJpaEntity entity) {
        return PriceRule.of(
                Sku.of(entity.getSku()),
                Money.of(entity.getBasePriceAmount(), entity.getCurrency()),
                Money.of(entity.getUnitCostAmount(), entity.getCurrency()),
                entity.getCategory(),
                MarginFloor.of(entity.getMarginFloorRatio()));
    }
}
