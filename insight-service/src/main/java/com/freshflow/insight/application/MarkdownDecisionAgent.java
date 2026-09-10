package com.freshflow.insight.application;

import com.freshflow.contracts.SellThroughRiskDetectedEvent;

/**
 * The port the LLM sits behind (FR14, ADR 0004).
 *
 * <p>One method, and it is the whole of the ReAct loop: given the risk that triggered it and the
 * tools it may use, the agent decides which evidence it needs, gathers it, and either proposes a
 * markdown or declines to. What comes back is an {@link AgentDeliberation} &mdash; a record of what
 * it did and what it concluded, with no side effects of its own.
 *
 * <p><b>Two adapters, and only one of them is ever on the test classpath.</b> A LangChain4j adapter
 * in {@code infrastructure} for runtime, and a deterministic stub in test sources for every test.
 * No test may call a live model or touch the network: an LLM is non-deterministic, rate-limited,
 * costs money per invocation and needs a key, and a test suite with any of those properties is not
 * a test suite. ADR 0004 records the ruling and gives the reviewer a mechanical check for it.
 *
 * <p>This is ADR 0001's argument applied to the least deterministic dependency in the system. The
 * point is not that the LLM might be swapped &mdash; it is that everything FR14 must be trusted to
 * do correctly, in particular the trace, the idempotency and the single publication, lives on the
 * other side of this interface and can be tested exactly.
 *
 * <p><b>The tool registry is a parameter, not a constructor dependency.</b> The agent is stateless
 * across runs, so the orchestrator owns the registry and hands it in; a test can then pass a
 * registry built over stub ports without constructing an agent for each one.
 *
 * <p>Declared in {@code application} per spec-01 &sect;2.3's port-placement table. It imports the
 * {@code contracts} module, which is the Published Language (spec-01 &sect;1.5) and drags in
 * neither Spring, JPA, Jackson nor Kafka; it imports nothing from {@code dev.langchain4j}, and
 * ADR 0004's verification check is that no type in this package ever does.
 */
public interface MarkdownDecisionAgent {

    AgentDeliberation deliberate(SellThroughRiskDetectedEvent trigger, AgentToolRegistry tools);
}
