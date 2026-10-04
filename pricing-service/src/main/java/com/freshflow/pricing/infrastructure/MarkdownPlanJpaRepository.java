package com.freshflow.pricing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository over {@link MarkdownPlanJpaEntity}.
 *
 * <p>An infrastructure detail, not the context's repository. That is
 * {@code infrastructure.repository.MarkdownPlanRepository}; {@link JpaMarkdownPlanRepository} adapts
 * this to that. Keeping them separate is what stops Spring Data's type from leaking inward.
 */
public interface MarkdownPlanJpaRepository extends JpaRepository<MarkdownPlanJpaEntity, String> {

    List<MarkdownPlanJpaEntity> findBySkuAndStatus(String sku, String status);

    Optional<MarkdownPlanJpaEntity> findFirstByAgentTraceIdOrderByCreatedAtAsc(String agentTraceId);
}
