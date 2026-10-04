package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.MarkdownPlanId;
import com.freshflow.pricing.domain.MarkdownStatus;
import com.freshflow.pricing.domain.Sku;
import com.freshflow.pricing.infrastructure.repository.MarkdownPlanRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adapter implementing {@code infrastructure.repository.MarkdownPlanRepository} (ADR 0005) over
 * Spring Data JPA.
 *
 * <p>The application layer is wired to the interface and never sees Spring Data, the entity or the
 * mapper. Substituting this adapter changes no application code.
 */
@Repository
public class JpaMarkdownPlanRepository implements MarkdownPlanRepository {

    private final MarkdownPlanJpaRepository jpaRepository;

    public JpaMarkdownPlanRepository(MarkdownPlanJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(MarkdownPlan plan) {
        jpaRepository.save(MarkdownPlanMapper.toEntity(plan));
    }

    @Override
    public Optional<MarkdownPlan> findById(MarkdownPlanId markdownPlanId) {
        return jpaRepository.findById(markdownPlanId.value()).map(MarkdownPlanMapper::toDomain);
    }

    @Override
    public List<MarkdownPlan> findActiveBySku(Sku sku) {
        return jpaRepository.findBySkuAndStatus(sku.value(), MarkdownStatus.ACTIVE.name())
                .stream()
                .map(MarkdownPlanMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<MarkdownPlan> findByAgentTraceId(String agentTraceId) {
        return jpaRepository.findFirstByAgentTraceIdOrderByCreatedAtAsc(agentTraceId)
                .map(MarkdownPlanMapper::toDomain);
    }
}
