package com.freshflow.insight.presentation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * spec-04 {@code BriefingResponse}, in its field order (FR15).
 *
 * <p>A rename of {@code EndOfDayBriefing} and nothing more, for the reason
 * {@link AgentTraceResponseDto} gives: the briefing is an audit record, and a presentation layer
 * that derived, summarised or reformatted any part of it would be answering a question other than
 * the one asked. {@code riskCount} and {@code recommendationCount} in particular are the
 * orchestrator's numbers, carried through untouched.
 *
 * <p>{@code toolCalls} arrives already sorted by {@code sequence} ascending, from the store, and
 * is not sorted here. The nested {@link ToolCall} record is declared inline because spec-04
 * declares it inline, exactly as it does for the agent trace.
 */
public record BriefingResponseDto(String briefingId,
                                  LocalDate businessDate,
                                  Instant generatedAt,
                                  String summary,
                                  int riskCount,
                                  int recommendationCount,
                                  List<ToolCall> toolCalls) {

    /** spec-04's {@code BriefingResponse.toolCalls} item, in its field order. */
    public record ToolCall(int sequence,
                           String toolName,
                           String arguments,
                           String result,
                           Instant timestamp) {
    }
}
