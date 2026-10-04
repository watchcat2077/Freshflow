package com.freshflow.insight.application;

import com.freshflow.insight.domain.BatchRiskView;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        return Set.of(LIST_CURRENT_RISKS, LIST_RECOMMENDATIONS, GET_SELL_THROUGH_RATE);
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
        if (toolName == null) {
            return "error=UNKNOWN_TOOL toolName=";
        }
        return switch (toolName) {
            case LIST_CURRENT_RISKS -> listCurrentRisks();
            case LIST_RECOMMENDATIONS -> listRecommendations(arguments);
            case GET_SELL_THROUGH_RATE -> getSellThroughRate(arguments);
            default -> "error=UNKNOWN_TOOL toolName=" + toolName;
        };
    }

    // ------------------------------------------------------------------------------- the tools

    /**
     * The batches currently flagged, worst first. Read through the same application service as
     * {@code GET /api/insight/risks}, so the briefing and the endpoint can never disagree about
     * which batches are at risk or in what order.
     */
    private String listCurrentRisks() {
        List<BatchRiskView> risks = riskQueryService.listCurrentRisks();
        if (risks.isEmpty()) {
            return NONE;
        }
        return risks.stream()
                .map(BriefingToolRegistry::render)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Every FR14 trace completed on the given UTC day, in completion order. The day's bounds come
     * from {@link BriefingRequest#startOfUtcDay}, which is also where the orchestrator's count
     * takes them from.
     */
    private String listRecommendations(String arguments) {
        String text = argument(arguments);
        if (text == null) {
            return "error=INVALID_ARGUMENTS " + LIST_RECOMMENDATIONS
                    + " expects businessDate as an ISO-8601 date";
        }

        LocalDate businessDate;
        try {
            businessDate = LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return "error=INVALID_ARGUMENTS businessDate=" + text + " is not an ISO-8601 date";
        }

        List<AgentTraceRecord> traces = agentTraceStore.findCompletedBetween(
                BriefingRequest.startOfUtcDay(businessDate),
                BriefingRequest.startOfUtcDay(businessDate.plusDays(1)));
        if (traces.isEmpty()) {
            return NONE;
        }
        return traces.stream()
                .map(BriefingToolRegistry::render)
                .collect(Collectors.joining("\n"));
    }

    /**
     * spec-06 &sect;4.2, by the same rendering FR14's tool uses, so that the two agents are told
     * the same thing about the same SKU. Unknown is reported as unknown and never as zero.
     */
    private String getSellThroughRate(String arguments) {
        String sku = argument(arguments);
        if (sku == null) {
            return "error=INVALID_ARGUMENTS " + GET_SELL_THROUGH_RATE + " expects sku";
        }
        return velocityQueryService.findCurrentVelocity(sku)
                .map(AgentToolRegistry::renderVelocity)
                .orElse("error=NO_CURRENT_VELOCITY sku=" + sku);
    }

    // --------------------------------------------------------------------------------- helpers

    private static String render(BatchRiskView view) {
        return "batchId=" + view.batchId()
                + " sku=" + view.sku()
                + " remainingQuantity=" + view.remainingQuantity()
                + " hoursToExpiry=" + view.hoursToExpiry()
                + " currentVelocity=" + view.currentVelocity()
                + " projectedShortfall=" + view.projectedShortfall();
    }

    /** The rationale is last because it is free text; everything before it is a single token. */
    private static String render(AgentTraceRecord trace) {
        return "batchId=" + trace.batchId()
                + " finalRecommendation=" + trace.finalRecommendation()
                + " rationale=" + trace.rationale();
    }

    /** The single-argument tools take one non-blank token; anything else is a tool failure. */
    private static String argument(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        return arguments.trim();
    }
}
