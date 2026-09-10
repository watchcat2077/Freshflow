package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.AgentTraceStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter implementing the {@code AgentTraceStore} port (FR14).
 *
 * <p>The dependency inversion of spec-01 &sect;2.2 made concrete for the one thing in this service
 * that is genuinely persisted. Its two read models are materialised by the Kafka Streams topology
 * and read back through Interactive Query; an agent trace is not derived from any stream, is
 * written once when a decision is made, and must survive a restart, so it is the only part of
 * {@code insight-service} that uses the H2 instance spec-01 &sect;1.3 gives it.
 *
 * <p>The application layer is wired to {@code AgentTraceStore} and has no idea JPA is underneath,
 * which is what lets every orchestration test run against an in-memory store with no database and
 * no Spring context.
 *
 * <p><b>The methods are transactional.</b> {@code toolCalls} is a lazy {@code @OneToMany}, and the
 * mapper reads it while turning an entity into a record; a read that arrived here without a
 * transaction &mdash; which is exactly how the controller calls it &mdash; would find the session
 * closed. {@code inventory-service}'s adapter needs no such annotation because its aggregate has no
 * lazy association.
 */
@Repository
public class JpaAgentTraceStore implements AgentTraceStore {

    private final AgentTraceJpaRepository jpaRepository;

    JpaAgentTraceStore(AgentTraceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Writes the trace and its calls in one transaction, the calls cascading from the trace.
     *
     * <p>No check for an existing row: spec-06 &sect;5's third mechanism is the unique constraint
     * on {@code trigger_event_id}, and a constraint that this adapter pre-empted with a read would
     * be a constraint with a race in it. A concurrent second delivery is meant to fail here.
     */
    @Override
    @Transactional
    public void save(AgentTraceRecord trace) {
        jpaRepository.save(AgentTraceMapper.toEntity(trace));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentTraceRecord> findByAgentTraceId(String agentTraceId) {
        return jpaRepository.findById(agentTraceId).map(AgentTraceMapper::toRecord);
    }

    /** spec-01 &sect;2.6's idempotency lookup, keyed on the trigger's {@code eventId}. */
    @Override
    @Transactional(readOnly = true)
    public Optional<AgentTraceRecord> findByTriggerEventId(String triggerEventId) {
        return jpaRepository.findByTriggerEventId(triggerEventId).map(AgentTraceMapper::toRecord);
    }
}
