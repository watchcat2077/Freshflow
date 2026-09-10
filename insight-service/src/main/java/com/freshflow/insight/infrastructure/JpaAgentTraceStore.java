package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.AgentTraceStore;
import org.springframework.stereotype.Repository;

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
 * <p><b>Phase A: signatures only.</b> The bodies are Phase B work.
 */
@Repository
public class JpaAgentTraceStore implements AgentTraceStore {

    private final AgentTraceJpaRepository jpaRepository;

    JpaAgentTraceStore(AgentTraceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(AgentTraceRecord trace) {
        throw new UnsupportedOperationException("FR14 Phase B");
    }

    @Override
    public Optional<AgentTraceRecord> findByAgentTraceId(String agentTraceId) {
        throw new UnsupportedOperationException("FR14 Phase B");
    }

    @Override
    public Optional<AgentTraceRecord> findByTriggerEventId(String triggerEventId) {
        throw new UnsupportedOperationException("FR14 Phase B");
    }
}
