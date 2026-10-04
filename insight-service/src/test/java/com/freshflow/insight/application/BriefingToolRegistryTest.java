package com.freshflow.insight.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.freshflow.insight.application.BriefingTestFixtures.PUBLISHED_A;
import static com.freshflow.insight.application.BriefingTestFixtures.SKU;
import static com.freshflow.insight.application.BriefingTestFixtures.registry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR15's tool set (spec-02 FR15: "three read-only tools"). Written BEFORE the implementation, so
 * <b>expected to FAIL</b>: {@code BriefingToolRegistry}'s methods throw
 * {@code UnsupportedOperationException("FR15")}.
 *
 * <p>As with FR14, the tool set is a security boundary as much as a feature list: whatever the
 * prompt says, an agent can do exactly what its tools let it do. For the briefing agent that must
 * be reading and only reading, and the first test says so mechanically.
 */
@DisplayName("FR15: the briefing tool registry")
class BriefingToolRegistryTest {

    private final BriefingToolRegistry tools = registry();

    // ------------------------------------------------------------------------------- the set

    /**
     * Exactly three, and the second half of the assertion carries the weight: no
     * {@code proposeMarkdown}, so the briefing agent cannot recommend anything however it is
     * prompted, and none of FR14's three later tools.
     */
    @Test
    @DisplayName("the registry offers exactly the three read-only tools and no writing tool")
    void toolNames_areExactlyTheThreeReadOnlyTools() {
        assertThat(tools.toolNames())
                .containsExactlyInAnyOrder(
                        BriefingToolRegistry.LIST_CURRENT_RISKS,
                        BriefingToolRegistry.LIST_RECOMMENDATIONS,
                        BriefingToolRegistry.GET_SELL_THROUGH_RATE)
                .as("spec-02 FR15: the agent proposes nothing and writes nothing")
                .doesNotContain("proposeMarkdown", "getBatchDetails",
                        "getMarkdownHistory", "getMarginFloor", "queryStorePolicy");
    }

    // ------------------------------------------------------------------------ listCurrentRisks

    /**
     * One batch per line, worst first, which is the order {@code GET /api/insight/risks} uses and
     * the order a store owner wants to read them in. The safe batch is absent entirely.
     */
    @Test
    @DisplayName("listCurrentRisks renders the flagged batches one per line, worst first")
    void listCurrentRisks_rendersFlaggedBatchesWorstFirstOnePerLine() {
        String result = tools.invoke(BriefingToolRegistry.LIST_CURRENT_RISKS, "");

        String[] lines = result.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0])
                .startsWith("batchId=BAT-WORST")
                .contains("sku=" + SKU)
                .contains("remainingQuantity=40")
                .contains("hoursToExpiry=10")
                .contains("currentVelocity=2.0")
                .contains("projectedShortfall=20.0");
        assertThat(lines[1]).startsWith("batchId=BAT-MILD");
        assertThat(result).doesNotContain("BAT-SAFE");
    }

    @Test
    @DisplayName("listCurrentRisks takes no argument, so whatever is passed is ignored")
    void listCurrentRisks_ignoresItsArgument() {
        String withNothing = tools.invoke(BriefingToolRegistry.LIST_CURRENT_RISKS, null);
        String withSomething = tools.invoke(BriefingToolRegistry.LIST_CURRENT_RISKS, "2026-10-04");

        assertThat(withSomething).isEqualTo(withNothing).doesNotStartWith("error=");
    }

    /** "Nothing is at risk" is a successful and informative answer, not an error. */
    @Test
    @DisplayName("listCurrentRisks answers none when no batch is flagged")
    void listCurrentRisks_whenNothingIsAtRisk_answersNone() {
        BriefingToolRegistry quiet = new BriefingToolRegistry(BriefingTestFixtures.noRisks(),
                BriefingTestFixtures.traces(), BriefingTestFixtures.velocityQueries());

        assertThat(quiet.invoke(BriefingToolRegistry.LIST_CURRENT_RISKS, ""))
                .isEqualTo(BriefingToolRegistry.NONE);
    }

    // -------------------------------------------------------------------- listRecommendations

    /**
     * Every trace completed on the business date, declined ones included, in completion order,
     * each as {@code batchId}, {@code finalRecommendation} and {@code rationale}. The fixture saved
     * them out of order, so the order asserted here is the store's and not insertion's.
     */
    @Test
    @DisplayName("listRecommendations lists the day's FR14 traces in completion order")
    void listRecommendations_listsTheDaysTracesInCompletionOrder() {
        String result = tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, "2026-10-04");

        String[] lines = result.split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("batchId=BAT-A finalRecommendation=" + PUBLISHED_A
                + " rationale=41 units will not clear before expiry at 2.0/h.");
        assertThat(lines[1]).startsWith("batchId=BAT-E finalRecommendation=PROPOSED_BUT_UNRESOLVABLE");
        assertThat(lines[2]).startsWith("batchId=BAT-B finalRecommendation=NO_MARKDOWN");
        assertThat(result)
                .as("the day is [00:00, 24:00) in UTC: the last second of the day before and the "
                        + "first instant of the day after both belong to other days")
                .doesNotContain("BAT-C")
                .doesNotContain("BAT-D");
    }

    @Test
    @DisplayName("listRecommendations honours both boundaries of the UTC day")
    void listRecommendations_includesEachBoundaryTraceInItsOwnDay() {
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, "2026-10-03"))
                .isEqualTo("batchId=BAT-D finalRecommendation=" + BriefingTestFixtures.PUBLISHED_D
                        + " rationale=Previous day's last decision.");
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, "2026-10-05"))
                .startsWith("batchId=BAT-C");
    }

    @Test
    @DisplayName("listRecommendations answers none for a day on which the agent decided nothing")
    void listRecommendations_whenNoTraceThatDay_answersNone() {
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, "2026-10-06"))
                .isEqualTo(BriefingToolRegistry.NONE);
    }

    /** A bad date is a tool failure the agent may correct, not an exception that ends the run. */
    @Test
    @DisplayName("listRecommendations rejects a missing or unparseable date as a tool failure")
    void listRecommendations_whenDateIsMissingOrMalformed_answersInvalidArguments() {
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, null))
                .isEqualTo("error=INVALID_ARGUMENTS listRecommendations expects businessDate as "
                        + "an ISO-8601 date");
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, "  "))
                .startsWith("error=INVALID_ARGUMENTS");
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, "yesterday"))
                .isEqualTo("error=INVALID_ARGUMENTS businessDate=yesterday is not an ISO-8601 date");
    }

    // --------------------------------------------------------------------- getSellThroughRate

    /** The same rendering as FR14's tool of the same name (spec-06 s4.2), by the same code. */
    @Test
    @DisplayName("getSellThroughRate renders the velocity and baseline exactly as FR14's tool does")
    void getSellThroughRate_rendersAsFr14Does() {
        assertThat(tools.invoke(BriefingToolRegistry.GET_SELL_THROUGH_RATE, SKU))
                .isEqualTo("velocityPerHour=120.0 baselineVelocity=120.0 velocityRatio=1.0");
    }

    /** Unknown is unknown, never zero (spec-06 s4.2). */
    @Test
    @DisplayName("getSellThroughRate reports an unknown rate as unknown, not as zero")
    void getSellThroughRate_whenNoCurrentVelocity_answersUnknown() {
        assertThat(tools.invoke(BriefingToolRegistry.GET_SELL_THROUGH_RATE, "SKU-NEVER-SOLD"))
                .isEqualTo("error=NO_CURRENT_VELOCITY sku=SKU-NEVER-SOLD");
        assertThat(tools.invoke(BriefingToolRegistry.GET_SELL_THROUGH_RATE, " "))
                .isEqualTo("error=INVALID_ARGUMENTS getSellThroughRate expects sku");
    }

    // ------------------------------------------------------------------------------- unknown

    @Test
    @DisplayName("a tool the registry does not offer is answered, not thrown")
    void invoke_unknownTool_answersUnknownTool() {
        assertThat(tools.invoke("proposeMarkdown", "BAT-A,PERCENT_30,2026-10-04T18:00:00Z,why"))
                .as("the writing tool is not merely absent from toolNames(); it does not run")
                .isEqualTo("error=UNKNOWN_TOOL toolName=proposeMarkdown");
        assertThat(tools.invoke(null, "")).isEqualTo("error=UNKNOWN_TOOL toolName=");
    }
}
