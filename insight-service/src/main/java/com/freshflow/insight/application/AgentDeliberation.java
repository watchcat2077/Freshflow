package com.freshflow.insight.application;

import java.util.List;

/**
 * What one run of the ReAct loop produced: the tool calls it made, in order, and the proposal it
 * arrived at, if it arrived at one.
 *
 * <p>This is {@link MarkdownDecisionAgent}'s return type, and it is the seam between the part of
 * FR14 that a language model performs and the part that ordinary code performs. Everything on this
 * record is evidence. Nothing on it has any effect: persisting the trace, publishing the
 * recommendation and deduplicating the trigger all belong to {@link MarkdownAgentOrchestrator},
 * and are therefore testable without a model and unchanged by whichever model sits behind the port.
 *
 * <p><b>{@code proposal} may be null, and that is a first-class outcome rather than a failure.</b>
 * An agent that examines a flagged batch and concludes that no markdown is warranted has done its
 * job. The trace is still written &mdash; FR14 requires the trace as the audit record of the
 * decision, and a decision not to act is still a decision &mdash; and nothing is published, because
 * there is nothing to recommend.
 *
 * <p>{@code toolCalls} is ordered by {@link ToolCall#sequence()} ascending, which is the order the
 * calls were made in. An empty list is legal and means the agent proposed without gathering
 * evidence; it is not filtered out here, because a trace that quietly hid such a run would defeat
 * the audit it exists for.
 */
public record AgentDeliberation(List<ToolCall> toolCalls,
                                MarkdownProposal proposal,
                                String rationale) {
}
