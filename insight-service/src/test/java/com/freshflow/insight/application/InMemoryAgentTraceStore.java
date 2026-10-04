package com.freshflow.insight.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test adapter for the {@code AgentTraceStore} port &mdash; the whole store, in a map.
 *
 * <p>Substituting an adapter behind a port is the property ADR 0001 relies on and
 * {@code InsightTestVelocityConfiguration} already uses for FR12 and FR13. It matters more here:
 * every FR14 orchestration rule worth testing &mdash; one trace per trigger, a trace even when
 * nothing is proposed, tool calls in order &mdash; is a statement about what was stored, and
 * asserting those against a database would test Hibernate rather than the use case.
 *
 * <p>Insertion order is preserved so that a test can say "exactly one trace was written" and mean
 * it, rather than inspecting a set.
 */
public final class InMemoryAgentTraceStore implements AgentTraceStore {

    private final Map<String, AgentTraceRecord> byAgentTraceId = new LinkedHashMap<>();

    @Override
    public void save(AgentTraceRecord trace) {
        byAgentTraceId.put(trace.agentTraceId(), trace);
    }

    @Override
    public Optional<AgentTraceRecord> findByAgentTraceId(String agentTraceId) {
        return Optional.ofNullable(byAgentTraceId.get(agentTraceId));
    }

    @Override
    public Optional<AgentTraceRecord> findByTriggerEventId(String triggerEventId) {
        return byAgentTraceId.values().stream()
                .filter(trace -> trace.triggerEventId().equals(triggerEventId))
                .findFirst();
    }

    /**
     * FR15's day-range read: {@code [from, to)}, in completion order with the identifier as the
     * tie-break, which is the order the JPA adapter promises.
     */
    @Override
    public List<AgentTraceRecord> findCompletedBetween(Instant from, Instant to) {
        return byAgentTraceId.values().stream()
                .filter(trace -> !trace.completedAt().isBefore(from)
                        && trace.completedAt().isBefore(to))
                .sorted(Comparator.comparing(AgentTraceRecord::completedAt)
                        .thenComparing(AgentTraceRecord::agentTraceId))
                .toList();
    }

    /** Every trace written, in the order it was written. */
    public List<AgentTraceRecord> all() {
        return new ArrayList<>(byAgentTraceId.values());
    }
}
