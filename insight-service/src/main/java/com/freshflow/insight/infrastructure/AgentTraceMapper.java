package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;

/**
 * Translates between FR14's read model and its JPA entity.
 *
 * <p>The direction of dependency is the point, exactly as it is for {@code StockBatchMapper} in
 * {@code inventory-service}: this class, in {@code infrastructure}, knows about
 * {@code application}. Nothing in {@code application} knows this class exists, and nothing in
 * {@code application} imports JPA (spec-01 &sect;2.2).
 *
 * <p>Both directions must preserve {@code sequence} order across the tool calls. The entity side
 * declares {@code @OrderBy} for the read direction; this class must not undo it by re-ordering, and
 * on the write direction must set the sequence numbers it is given rather than the position in a
 * list.
 *
 * <p><b>Phase A: signatures only.</b> The bodies are Phase B work, so that the round-trip test
 * fails on this exception rather than on a build that does not compile.
 */
final class AgentTraceMapper {

    private AgentTraceMapper() {
    }

    static AgentTraceEntity toEntity(AgentTraceRecord trace) {
        throw new UnsupportedOperationException("FR14 Phase B");
    }

    static AgentTraceRecord toRecord(AgentTraceEntity entity) {
        throw new UnsupportedOperationException("FR14 Phase B");
    }
}
