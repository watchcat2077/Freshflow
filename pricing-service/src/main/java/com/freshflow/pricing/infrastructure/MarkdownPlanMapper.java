package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.BatchId;
import com.freshflow.pricing.domain.DiscountTier;
import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.MarkdownPlanId;
import com.freshflow.pricing.domain.MarkdownStatus;
import com.freshflow.pricing.domain.Money;
import com.freshflow.pricing.domain.PromotionWindow;
import com.freshflow.pricing.domain.ProposalOrigin;
import com.freshflow.pricing.domain.Sku;

/**
 * Translates between the persistence-ignorant aggregate and its JPA entity (spec-01 &sect;2.3).
 *
 * <p>The direction of dependency is the point: this class, in {@code infrastructure}, knows about
 * {@code domain}. Nothing in {@code domain} knows this class exists.
 */
final class MarkdownPlanMapper {

    private MarkdownPlanMapper() {
    }

    static MarkdownPlanJpaEntity toEntity(MarkdownPlan plan) {
        Money discountedPrice = plan.discountedPrice();
        return new MarkdownPlanJpaEntity(
                plan.markdownPlanId().value(),
                plan.batchId().value(),
                plan.sku().value(),
                plan.discountTier().name(),
                plan.window().effectiveFrom(),
                plan.window().effectiveTo(),
                plan.status().name(),
                plan.origin().name(),
                plan.agentTraceId(),
                plan.rationale(),
                plan.rejectionReason(),
                discountedPrice == null ? null : discountedPrice.amount(),
                discountedPrice == null ? null : discountedPrice.currency(),
                plan.createdAt());
    }

    static MarkdownPlan toDomain(MarkdownPlanJpaEntity entity) {
        Money discountedPrice = entity.getDiscountedPriceAmount() == null
                ? null
                : Money.of(entity.getDiscountedPriceAmount(), entity.getDiscountedPriceCurrency());
        return MarkdownPlan.rehydrate(
                MarkdownPlanId.of(entity.getMarkdownPlanId()),
                BatchId.of(entity.getBatchId()),
                Sku.of(entity.getSku()),
                DiscountTier.valueOf(entity.getDiscountTier()),
                PromotionWindow.of(entity.getEffectiveFrom(), entity.getEffectiveTo()),
                MarkdownStatus.valueOf(entity.getStatus()),
                ProposalOrigin.valueOf(entity.getOrigin()),
                entity.getRationale(),
                entity.getAgentTraceId(),
                entity.getRejectionReason(),
                discountedPrice,
                entity.getCreatedAt());
    }
}
