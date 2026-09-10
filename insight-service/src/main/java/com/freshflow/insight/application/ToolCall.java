package com.freshflow.insight.application;

import java.time.Instant;

/**
 * One tool invocation made by the Markdown Decision Agent, as it is recorded in the trace (FR14).
 *
 * <p>Field for field spec-04's {@code AgentTraceResponse.toolCalls} item, in its order:
 * {@code sequence}, {@code toolName}, {@code arguments}, {@code result}, {@code timestamp}. The
 * response DTO is a rename of this record and nothing more, which is deliberate &mdash; a trace
 * whose stored shape differs from its published shape invites the two to drift.
 *
 * <p><b>{@code sequence} is the order, and it is stored rather than inferred.</b> spec-04 declares
 * it as a property of each call, so a consumer is entitled to sort by it. Relying instead on the
 * order a database happens to return rows in, or on {@code timestamp}, would be a guess: two calls
 * a language model issues in the same millisecond are indistinguishable by clock and are perfectly
 * distinguishable by sequence. It counts from 1 and increases by one per call within one trace.
 *
 * <p>{@code arguments} and {@code result} are {@code String} because spec-04 types them so. They
 * are the ReAct loop's own text &mdash; what the agent asked for and what it was told &mdash; kept
 * verbatim rather than re-rendered from a parsed structure, because the audit question FR14 exists
 * to answer is what the agent actually saw.
 *
 * <p>Declared in {@code application}: this service has no {@code domain} package (spec-01
 * &sect;2.3), and a trace is a CQRS read model rather than transactional business data.
 */
public record ToolCall(int sequence,
                       String toolName,
                       String arguments,
                       String result,
                       Instant timestamp) {
}
