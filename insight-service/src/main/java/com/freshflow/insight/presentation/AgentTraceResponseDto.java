package com.freshflow.insight.presentation;

import java.time.Instant;
import java.util.List;

/**
 * spec-04 {@code AgentTraceResponse}, in its field order (FR14).
 *
 * <p>A rename of {@code AgentTraceRecord} and nothing more. There is no computation on this side of
 * the boundary and there must not be: the trace is an audit record, and a presentation layer that
 * derived, summarised or reformatted any part of it would be answering a question other than the
 * one asked, which for an audit record is the whole failure mode.
 *
 * <p>{@code toolCalls} arrives already sorted by {@code sequence} ascending, from the store. The
 * controller does not sort it. That is deliberate and is asserted by the endpoint test: ordering is
 * a property of the stored trace, and a controller that re-sorted would hide a store that had lost
 * the order.
 *
 * <p>The nested {@link ToolCall} record is declared here rather than as a top-level type because
 * spec-04 declares it inline, as an anonymous object within this schema. It has no meaning apart
 * from the trace that contains it, and no endpoint returns one on its own.
 */
public record AgentTraceResponseDto(String agentTraceId,
                                    String triggerEventId,
                                    String batchId,
                                    List<ToolCall> toolCalls,
                                    String finalRecommendation,
                                    String rationale,
                                    Instant completedAt) {

    /** spec-04's {@code AgentTraceResponse.toolCalls} item, in its field order. */
    public record ToolCall(int sequence,
                           String toolName,
                           String arguments,
                           String result,
                           Instant timestamp) {
    }
}
