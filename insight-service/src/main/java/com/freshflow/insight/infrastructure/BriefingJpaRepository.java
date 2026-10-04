package com.freshflow.insight.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@link BriefingEntity} (FR15).
 *
 * <p>Package-private and confined to {@code infrastructure}, as {@link AgentTraceJpaRepository} is.
 * It is not the port: the port is {@code BriefingStore} in {@code application}, and
 * {@link JpaBriefingStore} is the adapter between the two (spec-01 &sect;2.2 keeps Spring Data out
 * of every layer but this one). The inherited {@code findById} and {@code save} are all FR15 needs.
 */
interface BriefingJpaRepository extends JpaRepository<BriefingEntity, String> {
}
