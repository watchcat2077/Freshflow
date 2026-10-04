package com.freshflow.insight.application;

import java.util.Set;

/**
 * The tools FR15's briefing agent may call, and the only ones it may call.
 *
 * <p><b>Three, exactly, and every one of them reads.</b> spec-02 FR15: "three read-only tools: the
 * current risk list, the markdown recommendations made on the business date, and the sell-through
 * rate of a SKU". There is no {@code proposeMarkdown} here and there must never be: the briefing
 * summarises decisions FR14 already made and recorded, and an agent whose tools cannot write
 * cannot write, whatever its prompt says. That is why the tool set is a separate class from
 * {@link AgentToolRegistry} rather than a flag on it &mdash; a registry that merely hid a writing
 * tool would be a registry one refactor away from exposing it.
 *
 * <table>
 *   <caption>The tools</caption>
 *   <tr><th>Tool</th><th>Argument</th><th>Reads through</th><th>Answers</th></tr>
 *   <tr><td>{@code listCurrentRisks}</td><td>none; any argument is ignored</td>
 *       <td>{@link RiskQueryService#listCurrentRisks()}, the same service behind
 *           {@code GET /api/insight/risks}</td>
 *       <td>one batch per line, worst first, as the endpoint orders them; {@code none} when
 *           nothing is flagged</td></tr>
 *   <tr><td>{@code listRecommendations}</td><td>{@code businessDate}, an ISO-8601 date</td>
 *       <td>{@link AgentTraceStore#findCompletedBetween} over the UTC day
 *           {@link BriefingRequest#startOfUtcDay} defines</td>
 *       <td>one FR14 trace per line &mdash; {@code batchId}, {@code finalRecommendation},
 *           {@code rationale} &mdash; in completion order; {@code none} when the agent decided
 *           nothing that day</td></tr>
 *   <tr><td>{@code getSellThroughRate}</td><td>{@code sku}</td>
 *       <td>{@link VelocityQueryService#findCurrentVelocity}, exactly as FR14's tool of the same
 *           name (spec-06 &sect;4.2)</td>
 *       <td>{@code velocityPerHour=…}, plus the baseline fields where a baseline is stored;
 *           {@code error=NO_CURRENT_VELOCITY} when no current window exists, never zero</td></tr>
 * </table>
 *
 * <p><b>{@code listRecommendations} lists every trace of the day, declined ones included.</b> A
 * store owner reviewing the day wants to know which batches the agent looked at and left alone as
 * much as which it marked down, and the {@code finalRecommendation} on each line says which is
 * which. What counts as a recommendation <i>made</i> &mdash; the number spec-04 reports as
 * {@code recommendationCount} &mdash; is {@link BriefingOrchestrator}'s decision, taken over the
 * same records, and is not inferred from this text.
 *
 * <p>Results are one line of {@code key=value} pairs per item, the rendering spec-06 &sect;4 chose
 * for FR14: stable enough to assert, plain enough to read in a trace. An unknown tool and a bad
 * argument are answered as {@code error=…} text rather than raised, so that a model that mis-calls
 * a tool is told so and may correct itself (spec-06 &sect;4).
 *
 * <p>It takes its collaborators as constructor arguments and reads no configuration, so a test
 * constructs one in a line with no Spring context. Its bean lives in {@code infrastructure}'s
 * {@code BriefingAgentConfiguration}.
 */
public class BriefingToolRegistry {

    /** spec-02 FR15: the batches currently flagged at risk of expiring unsold (FR13's list). */
    public static final String LIST_CURRENT_RISKS = "listCurrentRisks";

    /** spec-02 FR15: the markdown decisions FR14 recorded on the business date. */
    public static final String LIST_RECOMMENDATIONS = "listRecommendations";

    /** spec-02 FR15: the current sell-through rate of one SKU, as FR14's tool reports it. */
    public static final String GET_SELL_THROUGH_RATE = "getSellThroughRate";

    /** What the two list tools answer when there is nothing to list. Not an error. */
    public static final String NONE = "none";

    private final RiskQueryService riskQueryService;
    private final AgentTraceStore agentTraceStore;
    private final VelocityQueryService velocityQueryService;

    public BriefingToolRegistry(RiskQueryService riskQueryService,
                                AgentTraceStore agentTraceStore,
                                VelocityQueryService velocityQueryService) {
        this.riskQueryService = riskQueryService;
        this.agentTraceStore = agentTraceStore;
        this.velocityQueryService = velocityQueryService;
    }

    /**
     * The names the agent may invoke. Exactly the three constants above, and nothing else.
     *
     * @return the tool names offered to the agent
     */
    public Set<String> toolNames() {
        throw new UnsupportedOperationException("FR15");
    }

    /**
     * Runs one tool and returns what the agent is told, verbatim, for the briefing to record.
     *
     * @param toolName  one of the three names above
     * @param arguments the call's argument, as the agent supplied it; ignored by
     *                  {@code listCurrentRisks}
     * @return the result text the agent sees, and the briefing records
     */
    public String invoke(String toolName, String arguments) {
        throw new UnsupportedOperationException("FR15");
    }
}
