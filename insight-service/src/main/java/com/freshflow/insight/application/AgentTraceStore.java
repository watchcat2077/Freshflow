package com.freshflow.insight.application;

import java.util.Optional;

/**
 * Outbound port &mdash; persistence for FR14's agent traces.
 *
 * <p>Declared in {@code application} for the same reason as every other port in this service
 * (spec-01 &sect;2.3, ADR 0002 Decision 2). Its adapter is {@code JpaAgentTraceStore} in
 * {@code infrastructure}: spec-01 &sect;2.2 forbids the application layer from importing JPA, so
 * the entity and this record are two different types with a mapper between them.
 *
 * <p><b>Why it may have finder methods when {@code SaleTransactionRecorder} may not.</b> ADR 0002
 * Decision 1 bans query methods on a persistence port, but it bans them for {@code sales-service}
 * and for a stated reason: {@code findById} on a Transaction Script service presupposes the
 * aggregate spec-01 &sect;2.3 denies it. There is no aggregate here to presuppose. An
 * {@link AgentTraceRecord} is a CQRS read model, this service is designated read-side in its
 * entirety, and the sibling ports {@code RiskViewStore} and {@code VelocityWindowStore} already
 * read. The prohibition is about the shape of an abstraction, not about the spelling of a method.
 *
 * <p>The two finders answer two different questions and both are needed:
 *
 * <ul>
 *   <li>{@link #findByAgentTraceId(String)} serves spec-04's
 *       {@code GET /api/insight/agent-traces/&#123;agentTraceId&#125;}.</li>
 *   <li>{@link #findByTriggerEventId(String)} is the idempotency check. spec-01 &sect;2.6 requires
 *       consumers to deduplicate on {@code eventId}, and this is where FR14 does it: a trigger
 *       whose trace already exists is answered with that trace, and no second run, no second
 *       trace and no second publication follow.</li>
 * </ul>
 *
 * <p>{@code save} is write-only and returns nothing. A trace is written once and never updated, so
 * there is no update method and no merge semantics to reason about.
 */
public interface AgentTraceStore {

    void save(AgentTraceRecord trace);

    Optional<AgentTraceRecord> findByAgentTraceId(String agentTraceId);

    Optional<AgentTraceRecord> findByTriggerEventId(String triggerEventId);
}
