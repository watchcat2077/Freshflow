package com.freshflow.insight.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.freshflow.insight.application.BriefingTestFixtures.CLOCK;
import static com.freshflow.insight.application.BriefingTestFixtures.NOW;
import static com.freshflow.insight.application.BriefingTestFixtures.SKU;
import static com.freshflow.insight.application.BriefingTestFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR15 orchestration &mdash; plain JUnit over the application layer, with no Spring context, no
 * database and no model. Written BEFORE the implementation (Continuous Auto-Validation), so
 * <b>these tests are expected to FAIL</b>: {@code BriefingOrchestrator.brief} throws
 * {@code UnsupportedOperationException("FR15")}.
 *
 * <p>Under test is everything about FR15 that must hold regardless of which model sits behind
 * {@code BriefingAgent}: that the counts come from the service's own records and not from the
 * model's text, that the business date defaults to today in UTC and is honoured when given, that a
 * blank narrative is handled rather than refused, and that the briefing is persisted with its tool
 * calls in order. Every one of those is a property of ordinary code, which is ADR 0004's argument
 * applied a second time.
 */
@DisplayName("FR15: briefing orchestration")
class BriefingOrchestratorTest {

    private static final String BRIEFING_ID = "BRF-[0-9A-F]{8}";

    private final InMemoryBriefingStore briefings = new InMemoryBriefingStore();
    private final RiskQueryService risks = BriefingTestFixtures.riskQueries();
    private final InMemoryAgentTraceStore traces = BriefingTestFixtures.traces();
    private final BriefingToolRegistry tools =
            new BriefingToolRegistry(risks, traces, BriefingTestFixtures.velocityQueries());

    private BriefingOrchestrator orchestratorWith(BriefingAgent agent) {
        return new BriefingOrchestrator(agent, tools, briefings, risks, traces, CLOCK);
    }

    // ------------------------------------------------------------------------------- grounding

    /**
     * The assertion that carries the weight is {@code riskCount} 2 and {@code recommendationCount}
     * 1 beside a summary that says ninety-nine and forty. spec-02 FR15: "no number in the response
     * depends on the model". Two batches are flagged in the risk store; three FR14 traces were
     * completed on the day, of which one was published, one declined and one could not be
     * resolved, and only the published one is a recommendation made.
     */
    @Test
    @DisplayName("counts come from the risk and trace stores, never from the model's text")
    void brief_computesCountsFromTheStoresNotFromTheSummary() {
        EndOfDayBriefing briefing =
                orchestratorWith(StubBriefingAgent.summarising(CLOCK, SKU)).brief(null);

        assertThat(briefing.summary())
                .as("the stub's narrative is deliberately wrong about both numbers")
                .isEqualTo(StubBriefingAgent.SUMMARY)
                .contains("Ninety-nine")
                .contains("forty");
        assertThat(briefing.riskCount()).isEqualTo(2);
        assertThat(briefing.recommendationCount())
                .as("NO_MARKDOWN and PROPOSED_BUT_UNRESOLVABLE are decisions, not recommendations")
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------------------- the date

    @Test
    @DisplayName("without a date, the briefing covers today in UTC from the injected clock")
    void brief_withoutADate_defaultsToTodayInUtc() {
        EndOfDayBriefing briefing =
                orchestratorWith(StubBriefingAgent.summarising(CLOCK, SKU)).brief(null);

        assertThat(briefing.businessDate()).isEqualTo(TODAY);
        assertThat(briefing.generatedAt()).isEqualTo(NOW);
        assertThat(briefing.toolCalls().get(1).arguments())
                .as("the agent is told which day it is briefing on; it does not work it out")
                .isEqualTo("2026-10-04");
    }

    /**
     * Both boundaries, from the orchestrator's side: the day before holds one published trace at
     * its last second and the day after holds one at its first instant.
     */
    @Test
    @DisplayName("an explicit date is honoured, with the UTC day's boundaries respected")
    void brief_withAnExplicitDate_countsThatDaysRecommendations() {
        BriefingOrchestrator orchestrator = orchestratorWith(StubBriefingAgent.summarising(CLOCK, SKU));

        EndOfDayBriefing dayBefore = orchestrator.brief(LocalDate.of(2026, 10, 3));
        EndOfDayBriefing dayAfter = orchestrator.brief(LocalDate.of(2026, 10, 5));
        EndOfDayBriefing quietDay = orchestrator.brief(LocalDate.of(2026, 10, 6));

        assertThat(dayBefore.businessDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(dayBefore.recommendationCount()).isEqualTo(1);
        assertThat(dayBefore.toolCalls().get(1).arguments()).isEqualTo("2026-10-03");
        assertThat(dayAfter.recommendationCount()).isEqualTo(1);
        assertThat(quietDay.recommendationCount()).isZero();
        assertThat(quietDay.riskCount())
                .as("the risk list is current, not historical; it is the same on any date")
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------------- the narrative

    /**
     * A model that writes nothing has still gathered evidence worth keeping. The orchestrator must
     * not throw and must not store a blank: it stores a fixed sentence saying what happened, and
     * the counts and the calls are as they would otherwise be.
     */
    @Test
    @DisplayName("a blank summary is replaced by a fixed sentence, and nothing else changes")
    void brief_whenTheAgentProducesNoNarrative_storesTheFixedSentence() {
        EndOfDayBriefing briefing =
                orchestratorWith(StubBriefingAgent.silent(CLOCK, SKU)).brief(null);

        assertThat(briefing.summary()).isEqualTo(BriefingOrchestrator.NO_NARRATIVE);
        assertThat(briefing.riskCount()).isEqualTo(2);
        assertThat(briefing.recommendationCount()).isEqualTo(1);
        assertThat(briefing.toolCalls()).hasSize(3);
        assertThat(briefings.all()).hasSize(1);
    }

    // ------------------------------------------------------------------------- the record

    /**
     * FR15: "the briefing and its ordered tool calls are persisted". The identifier format is
     * ours; what matters is that it is set, that the stored briefing is the returned one, and that
     * the calls carry {@code sequence} 1, 2, 3 in the order they were made.
     */
    @Test
    @DisplayName("the briefing is persisted under a BRF- id with its tool calls in call order")
    void brief_persistsTheBriefingWithOrderedToolCalls() {
        EndOfDayBriefing briefing =
                orchestratorWith(StubBriefingAgent.summarising(CLOCK, SKU)).brief(null);

        assertThat(briefing.briefingId()).matches(BRIEFING_ID);
        assertThat(briefings.all()).containsExactly(briefing);
        assertThat(briefings.findById(briefing.briefingId())).contains(briefing);

        assertThat(briefing.toolCalls())
                .extracting(ToolCall::sequence)
                .containsExactly(1, 2, 3);
        assertThat(briefing.toolCalls())
                .extracting(ToolCall::toolName)
                .containsExactly(BriefingToolRegistry.LIST_CURRENT_RISKS,
                        BriefingToolRegistry.LIST_RECOMMENDATIONS,
                        BriefingToolRegistry.GET_SELL_THROUGH_RATE);
        assertThat(briefing.toolCalls()).allSatisfy(call -> {
            assertThat(call.result())
                    .as("a recorded call must carry what the agent was told, and the fixtures "
                            + "answer every call the stub makes")
                    .isNotBlank()
                    .doesNotStartWith("error=");
            assertThat(call.timestamp()).isEqualTo(NOW);
        });
    }

    /** A POST is a new briefing each time, generated from the records as they stand. */
    @Test
    @DisplayName("every briefing gets its own identifier and its own record")
    void brief_mintsADistinctIdPerBriefing() {
        BriefingOrchestrator orchestrator = orchestratorWith(StubBriefingAgent.summarising(CLOCK, SKU));

        EndOfDayBriefing first = orchestrator.brief(null);
        EndOfDayBriefing second = orchestrator.brief(null);

        assertThat(second.briefingId()).isNotEqualTo(first.briefingId());
        assertThat(briefings.all()).hasSize(2);
    }
}
