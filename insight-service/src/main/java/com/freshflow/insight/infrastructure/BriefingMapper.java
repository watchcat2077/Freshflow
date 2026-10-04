package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.EndOfDayBriefing;
import com.freshflow.insight.application.ToolCall;

import java.util.List;

/**
 * Translates between FR15's read model and its JPA entity.
 *
 * <p>The direction of dependency is the point, exactly as it is for {@link AgentTraceMapper}: this
 * class, in {@code infrastructure}, knows about {@code application}. Nothing in {@code application}
 * knows this class exists, and nothing in {@code application} imports JPA (spec-01 &sect;2.2).
 *
 * <p>Both directions must preserve {@code sequence} order across the tool calls. The entity side
 * declares {@code @OrderBy} for the read direction; this class must not undo it by re-ordering, and
 * on the write direction must set the sequence numbers it is given rather than the position in a
 * list.
 */
final class BriefingMapper {

    private BriefingMapper() {
    }

    static BriefingEntity toEntity(EndOfDayBriefing briefing) {
        BriefingEntity entity = new BriefingEntity(
                briefing.briefingId(),
                briefing.businessDate(),
                briefing.generatedAt(),
                briefing.summary(),
                briefing.riskCount(),
                briefing.recommendationCount());

        // The sequence written is the one the agent recorded, never the position in this list.
        for (ToolCall call : briefing.toolCalls()) {
            entity.addToolCall(new BriefingToolCallEntity(
                    call.sequence(),
                    call.toolName(),
                    call.arguments(),
                    call.result(),
                    call.timestamp()));
        }
        return entity;
    }

    static EndOfDayBriefing toRecord(BriefingEntity entity) {
        List<ToolCall> toolCalls = entity.getToolCalls().stream()
                .map(call -> new ToolCall(
                        call.getSequence(),
                        call.getToolName(),
                        call.getArguments(),
                        call.getResult(),
                        call.getTimestamp()))
                .toList();

        return new EndOfDayBriefing(
                entity.getBriefingId(),
                entity.getBusinessDate(),
                entity.getGeneratedAt(),
                entity.getSummary(),
                entity.getRiskCount(),
                entity.getRecommendationCount(),
                toolCalls);
    }
}
