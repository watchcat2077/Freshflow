package com.freshflow.insight.application;

import java.time.Instant;
import java.util.List;

/**
 * spec-03 &sect;4's {@code AgentTraceRecord} &mdash; the persisted, queryable record of one agent
 * decision (FR14: "full tool-call trace persisted and queryable by {@code agentTraceId}").
 *
 * <p>Field for field spec-04's {@code AgentTraceResponse}, so that
 * {@code GET /api/insight/agent-traces/&#123;agentTraceId&#125;} is a rename and not a
 * transformation.
 *
 * <p><b>It is a read model, not an aggregate.</b> spec-01 &sect;2.3 gives this service no
 * {@code domain} package at all, so the record lives in {@code application} beside
 * {@link SkuVelocityView} and {@link BatchRiskView}, and the JPA entity that stores it is a
 * separate type in {@code infrastructure} with a mapper between them (spec-01 &sect;2.2: the
 * application layer imports no JPA).
 *
 * <p><b>Unlike the other two read models, it is not maintained by the topology.</b> The velocity
 * and risk views are continuously recomputed from the stream and are current by construction; a
 * trace is written once, when a decision is made, and never changes afterwards. That is what makes
 * it an audit record: a trace that could be recomputed could also be recomputed differently.
 *
 * <p>{@code triggerEventId} is the {@code eventId} of the {@code sell-through-risk-detected} event
 * that caused this run. It is stored rather than merely used, because it is the idempotency key
 * (spec-01 &sect;2.6): one trigger yields at most one trace, and a redelivery finds this one.
 *
 * <p>{@code finalRecommendation} is a short machine-readable summary of the outcome and
 * {@code rationale} is FR14's written justification for it. Both are present when the agent
 * declined to propose: the recommendation records that none was made, and the rationale says why.
 * Neither is ever computed from a price.
 *
 * <p>{@code completedAt} is a wall-clock instant, and is the one time field in this service that
 * legitimately is. spec-01 &sect;2.6's event-time rule governs windowing and the arithmetic derived
 * from it; when a decision was recorded is a fact about this service's own operation, not a
 * measurement of the business.
 */
public record AgentTraceRecord(String agentTraceId,
                               String triggerEventId,
                               String batchId,
                               List<ToolCall> toolCalls,
                               String finalRecommendation,
                               String rationale,
                               Instant completedAt) {
}
