package com.freshflow.insight.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * spec-03 &sect;4's {@code EndOfDayBriefing} &mdash; the persisted, queryable record of one FR15
 * briefing, field for field spec-04's {@code BriefingResponse} so that
 * {@code GET /api/insight/briefings/&#123;briefingId&#125;} is a rename and not a transformation.
 *
 * <p><b>It is a read model, not an aggregate</b>, and it lives in {@code application} beside
 * {@link AgentTraceRecord} for the reason ADR 0006 gives: the Insight context's {@code domain}
 * package holds pure rules and the values they produce, while the persisted records of what the
 * agents did stay on the read side with their stores. The JPA entity that stores it is a separate
 * type in {@code infrastructure} with a mapper between them (spec-01 &sect;2.2).
 *
 * <p><b>{@code riskCount} and {@code recommendationCount} are not the model's numbers.</b> spec-02
 * FR15 is explicit: "the orchestrator computes {@code riskCount} and {@code recommendationCount}
 * itself from the tool results so no number in the response depends on the model".
 * {@link BriefingOrchestrator} computes both from the same services the tools read, after the
 * agent has finished, and the {@code summary} is free to be wrong about them without any number
 * in this record being wrong. That is the whole reason the two fields exist beside the text.
 *
 * <p>{@code toolCalls} is what makes the briefing auditable, for the same reason spec-03 &sect;4
 * gives for the agent trace: "a briefing that cannot be checked against the evidence it was
 * written from is not auditable". The calls arrive ordered by {@link ToolCall#sequence()} and the
 * order survives persistence, as it does for FR14 (spec-06 &sect;6).
 *
 * <p>{@code generatedAt} is wall-clock, from an injected {@code Clock}; {@code businessDate} is
 * the UTC calendar day the briefing covers (spec-04).
 */
public record EndOfDayBriefing(String briefingId,
                               LocalDate businessDate,
                               Instant generatedAt,
                               String summary,
                               int riskCount,
                               int recommendationCount,
                               List<ToolCall> toolCalls) {
}
