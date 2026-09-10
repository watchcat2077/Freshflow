package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.ToolCall;

import java.util.List;

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
 */
final class AgentTraceMapper {

    private AgentTraceMapper() {
    }

    static AgentTraceEntity toEntity(AgentTraceRecord trace) {
        AgentTraceEntity entity = new AgentTraceEntity(
                trace.agentTraceId(),
                trace.triggerEventId(),
                trace.batchId(),
                trace.finalRecommendation(),
                trace.rationale(),
                trace.completedAt());

        // The sequence written is the one the agent recorded, never the position in this list:
        // spec-04 declares sequence as a property of the call, and the calls may arrive in any
        // order. The read direction is ordered by the entity's @OrderBy, not by this loop.
        for (ToolCall call : trace.toolCalls()) {
            entity.addToolCall(new ToolCallEntity(
                    call.sequence(),
                    call.toolName(),
                    call.arguments(),
                    call.result(),
                    call.timestamp()));
        }
        return entity;
    }

    static AgentTraceRecord toRecord(AgentTraceEntity entity) {
        List<ToolCall> toolCalls = entity.getToolCalls().stream()
                .map(call -> new ToolCall(
                        call.getSequence(),
                        call.getToolName(),
                        call.getArguments(),
                        call.getResult(),
                        call.getTimestamp()))
                .toList();

        return new AgentTraceRecord(
                entity.getAgentTraceId(),
                entity.getTriggerEventId(),
                entity.getBatchId(),
                toolCalls,
                entity.getFinalRecommendation(),
                entity.getRationale(),
                entity.getCompletedAt());
    }
}
