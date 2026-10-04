package com.freshflow.insight.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic stand-in for FR15's language model, and the only {@code BriefingAgent} any
 * test ever sees (ADR 0004, R1, applied to FR15 unchanged).
 *
 * <p><b>What it is a stand-in for.</b> A model's <i>behaviour as a tool-using loop</i>, not its
 * judgement. It calls the three tools through the real {@link BriefingToolRegistry}, in a fixed
 * order, records each call with its argument and its result, and then writes a fixed summary.
 * Everything downstream is therefore exercised for real: the counts, the persisted record, the
 * ordering, the endpoint.
 *
 * <p><b>The summary is wrong on purpose.</b> It claims ninety-nine batches are at risk and forty
 * markdowns were recommended. spec-02 FR15 requires that "no number in the response depends on the
 * model", and the cleanest way to prove the orchestrator obeys is to hand it a model that lies
 * about the numbers and assert that the response does not.
 *
 * <p>Two modes, because a model can also say nothing: {@link #summarising} writes the summary
 * above; {@link #silent} makes the same tool calls and returns a blank summary, which the
 * orchestrator must replace rather than refuse.
 */
public final class StubBriefingAgent implements BriefingAgent {

    /** The narrative a summarising stub always writes. Its numbers are deliberately false. */
    public static final String SUMMARY = "Ninety-nine batches are at risk tonight and forty "
            + "markdowns were recommended today, which is wrong on purpose: no number in a briefing "
            + "may come from this text. Write-off totals are not available because write-offs are "
            + "not recorded.";

    private final Clock clock;
    private final String sku;
    private final boolean narrates;

    private StubBriefingAgent(Clock clock, String sku, boolean narrates) {
        this.clock = clock;
        this.sku = sku;
        this.narrates = narrates;
    }

    /** Calls all three tools, looking up {@code sku}'s sell-through rate, then writes the summary. */
    public static StubBriefingAgent summarising(Clock clock, String sku) {
        return new StubBriefingAgent(clock, sku, true);
    }

    /** Makes the same calls and then writes nothing. */
    public static StubBriefingAgent silent(Clock clock, String sku) {
        return new StubBriefingAgent(clock, sku, false);
    }

    @Override
    public BriefingDeliberation brief(BriefingRequest request, BriefingToolRegistry tools) {
        List<ToolCall> calls = new ArrayList<>();
        calls.add(invoke(tools, calls.size() + 1, BriefingToolRegistry.LIST_CURRENT_RISKS, ""));
        calls.add(invoke(tools, calls.size() + 1, BriefingToolRegistry.LIST_RECOMMENDATIONS,
                request.businessDate().toString()));
        calls.add(invoke(tools, calls.size() + 1, BriefingToolRegistry.GET_SELL_THROUGH_RATE, sku));

        return new BriefingDeliberation(List.copyOf(calls), narrates ? SUMMARY : "   ");
    }

    private ToolCall invoke(BriefingToolRegistry tools, int sequence, String toolName,
                            String arguments) {
        String result = tools.invoke(toolName, arguments);
        return new ToolCall(sequence, toolName, arguments, result, clock.instant());
    }
}
