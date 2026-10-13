package com.freshflow.pricing.infrastructure.repository;

import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.MarkdownPlanId;
import com.freshflow.pricing.domain.Sku;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for {@code MarkdownPlan} (spec-03 &sect;3), declared in
 * {@code infrastructure.repository} per ADR 0005. The JPA
 * implementation lives beside it in {@code infrastructure}.
 *
 * <p>The signature speaks only the domain's language &mdash; {@link MarkdownPlan},
 * {@link MarkdownPlanId}, {@link Sku} &mdash; and says nothing about how or where anything is
 * stored. ADR 0005's check 3 holds this file to that.
 *
 * <p>{@link #findByAgentTraceId} is not in spec-03's three-method sketch. It exists for the
 * {@code markdown-recommended} consumer, which must be idempotent on the agent's trace id: the
 * same recommendation may be redelivered, or republished under a new {@code eventId}, and must
 * produce one plan.
 */
public interface MarkdownPlanRepository {

    void save(MarkdownPlan plan);

    Optional<MarkdownPlan> findById(MarkdownPlanId markdownPlanId);

    /** Plans in status ACTIVE for the SKU, whether or not their window contains now. */
    List<MarkdownPlan> findActiveBySku(Sku sku);

    /** The plan recorded for an agent deliberation, whatever its status. */
    Optional<MarkdownPlan> findByAgentTraceId(String agentTraceId);
}
