package com.freshflow.insight.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository over {@link AgentTraceEntity}.
 *
 * <p>Package-private and confined to {@code infrastructure}. It is not the port: the port is
 * {@code AgentTraceStore} in {@code application}, and {@link JpaAgentTraceStore} is the adapter
 * between the two. The same arrangement as {@code inventory-service}'s
 * {@code StockBatchJpaRepository} behind {@code JpaStockBatchRepository}, and for the same reason
 * &mdash; spec-01 &sect;2.2 keeps Spring Data out of every layer but this one.
 *
 * <p>{@code findByTriggerEventId} is the idempotency lookup of spec-01 &sect;2.6, and pairs with
 * the unique constraint {@link AgentTraceEntity} declares on that column.
 *
 * <p>The range query is FR15's (4 October 2026): the traces completed within one UTC business day,
 * lower bound inclusive and upper bound exclusive, in completion order with the identifier as the
 * tie-break so that two traces completed in the same instant come back in a stable order.
 */
interface AgentTraceJpaRepository extends JpaRepository<AgentTraceEntity, String> {

    Optional<AgentTraceEntity> findByTriggerEventId(String triggerEventId);

    List<AgentTraceEntity> findByCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscAgentTraceIdAsc(
            Instant from, Instant to);
}
