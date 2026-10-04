package com.freshflow.insight.application;

/**
 * The port FR15's language model sits behind (spec-02 FR15: "a second agent, behind its own port").
 *
 * <p>One method, and it is the whole of the agent's work: given the business date and the three
 * read-only tools it may use, gather the day's evidence and write a short briefing for the store
 * owner. What comes back is a {@link BriefingDeliberation} &mdash; what it called and what it
 * wrote &mdash; with no side effects of its own.
 *
 * <p><b>Two adapters, and only one of them is ever seen by a test</b> (ADR 0004, team ruling R1,
 * applied to FR15 unchanged). {@code LangChain4jBriefingAgent} in {@code infrastructure} for
 * runtime, and {@code StubBriefingAgent} in test sources for every test. No test calls a live
 * model or touches the network: the stub is deterministic, so a test can assert a briefing rather
 * than assert that one exists.
 *
 * <p><b>This agent reads and nothing else.</b> Its tool registry is a different type from FR14's
 * on purpose: {@link BriefingToolRegistry} offers the current risk list, the day's recommendations
 * and the sell-through rate of a SKU, and there is no {@code proposeMarkdown} to be found. The
 * briefing summarises decisions that have already been made and recorded; it proposes nothing and
 * writes nothing, and team ruling R5 applies to it exactly as it applies to FR14.
 *
 * <p>The registry is a parameter rather than a constructor dependency, as it is for
 * {@link MarkdownDecisionAgent}: the agent is stateless across runs and the orchestrator owns the
 * registry.
 *
 * <p>Declared in {@code application} per spec-01 &sect;2.3's port-placement table and ADR 0004.
 * It imports nothing from {@code dev.langchain4j}; ADR 0004's first verification check is that no
 * type in this package ever does.
 */
public interface BriefingAgent {

    BriefingDeliberation brief(BriefingRequest request, BriefingToolRegistry tools);
}
