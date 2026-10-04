package com.freshflow.insight.application;

import java.util.List;

/**
 * What one run of the briefing agent produced (FR15): the tool calls it made, in order, and the
 * plain-language summary it wrote.
 *
 * <p>This is {@link BriefingAgent}'s return type, and it is FR15's seam between what a language
 * model does and what ordinary code does, exactly as {@link AgentDeliberation} is FR14's. Everything
 * on it is evidence and nothing on it has an effect: minting the identifier, computing the counts,
 * persisting the briefing and answering the request all belong to {@link BriefingOrchestrator}, on
 * the deterministic side of the port, where tests can assert them without a model.
 *
 * <p><b>{@code summary} may be blank, and that is handled rather than refused.</b> A model that
 * calls its tools and then stops without writing, or one that runs out of steps, has still
 * gathered evidence worth keeping. The orchestrator stores a fixed sentence in place of the missing
 * narrative and the tool calls are persisted either way, so the briefing remains auditable even
 * when it is not readable. spec-02 FR15 forbids throwing on what a model said or failed to say:
 * "no number in the response depends on the model", and neither does whether there is a response.
 *
 * <p>{@code toolCalls} is ordered by {@link ToolCall#sequence()} ascending, which is the order the
 * calls were made in. It is reused from FR14 unchanged, because spec-04 declares the same
 * {@code toolCalls} item on both responses.
 */
public record BriefingDeliberation(List<ToolCall> toolCalls, String summary) {
}
