package com.freshflow.insight.application;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic stand-in for the LLM, and the only {@code MarkdownDecisionAgent} any test ever
 * sees (ADR 0004, R1).
 *
 * <p><b>It lives in test sources so that LangChain4j never reaches the test classpath.</b> That is
 * not tidiness. A test that could reach a model could reach the network, cost money, need an API
 * key and give a different answer on Tuesday; ADR 0004's verification check is that no test
 * resolves a live model, and the simplest way to keep a promise like that is to make it impossible
 * to break.
 *
 * <p><b>What it is a stand-in for.</b> Not for a model's judgement &mdash; for a model's
 * <i>behaviour as a ReAct loop</i>. It calls tools through the real {@link AgentToolRegistry}, in a
 * fixed order, and records each call with its arguments and its result, which is exactly the shape
 * of thing the LangChain4j adapter will produce. Everything downstream of this class is therefore
 * exercised for real: the trace, the ordering, the idempotency, the single publication.
 *
 * <p><b>Determinism is the whole design.</b> The tool sequence is fixed, the tier is fixed, the
 * rationale is fixed, and every timestamp comes from an injected {@link Clock}. Given the same
 * trigger it produces byte-identical output, which is what lets a test assert a trace rather than
 * assert that a trace exists.
 *
 * <p>Two modes, because FR14 has two outcomes and only one of them is the happy path:
 * {@link #proposing(Clock)} gathers evidence and proposes; {@link #declining(Clock)} gathers the
 * same evidence and concludes that no markdown is warranted. A declining run still makes tool calls
 * and still yields a trace &mdash; the trace is the audit record of the decision, not a by-product
 * of having decided to act.
 */
public final class StubMarkdownDecisionAgent implements MarkdownDecisionAgent {

    /** The tier a proposing stub always recommends. */
    public static final DiscountTier TIER = DiscountTier.PERCENT_30;

    /** The rationale a proposing stub always gives. */
    public static final String PROPOSAL_RATIONALE =
            "Remaining quantity will not clear before expiry at the current sell-through rate.";

    /** The rationale a declining stub always gives. */
    public static final String DECLINED_RATIONALE =
            "Sell-through has recovered since the assessment; no markdown is warranted.";

    private final Clock clock;
    private final boolean proposes;

    private StubMarkdownDecisionAgent(Clock clock, boolean proposes) {
        this.clock = clock;
        this.proposes = proposes;
    }

    /** Gathers evidence through both read tools, then proposes. */
    public static StubMarkdownDecisionAgent proposing(Clock clock) {
        return new StubMarkdownDecisionAgent(clock, true);
    }

    /** Gathers the same evidence, then declines to propose. */
    public static StubMarkdownDecisionAgent declining(Clock clock) {
        return new StubMarkdownDecisionAgent(clock, false);
    }

    @Override
    public AgentDeliberation deliberate(SellThroughRiskDetectedEvent trigger,
                                        AgentToolRegistry tools) {
        List<ToolCall> calls = new ArrayList<>();
        calls.add(invoke(tools, calls.size() + 1,
                AgentToolRegistry.GET_BATCH_DETAILS, trigger.batchId()));
        calls.add(invoke(tools, calls.size() + 1,
                AgentToolRegistry.GET_SELL_THROUGH_RATE, trigger.sku()));

        if (!proposes) {
            return new AgentDeliberation(List.copyOf(calls), null, DECLINED_RATIONALE);
        }

        Instant effectiveFrom = clock.instant();
        MarkdownProposal proposal = new MarkdownProposal(
                trigger.batchId(), TIER, effectiveFrom, PROPOSAL_RATIONALE);
        calls.add(invoke(tools, calls.size() + 1, AgentToolRegistry.PROPOSE_MARKDOWN,
                trigger.batchId() + "," + TIER + "," + effectiveFrom + "," + PROPOSAL_RATIONALE));

        return new AgentDeliberation(List.copyOf(calls), proposal, PROPOSAL_RATIONALE);
    }

    private ToolCall invoke(AgentToolRegistry tools, int sequence, String toolName,
                            String arguments) {
        String result = tools.invoke(toolName, arguments);
        return new ToolCall(sequence, toolName, arguments, result, clock.instant());
    }
}
