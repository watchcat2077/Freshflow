package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.BriefingStore;
import com.freshflow.insight.application.EndOfDayBriefing;
import com.freshflow.insight.application.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR15's briefing persistence &mdash; a {@code @DataJpaTest} slice over the real H2 instance
 * spec-01 &sect;1.3 gives this service. Written BEFORE the implementation, so <b>expected to
 * FAIL</b>: {@code JpaBriefingStore} and {@code BriefingMapper} throw
 * {@code UnsupportedOperationException("FR15")}.
 *
 * <p>What only a database can answer, as for FR14's trace, is whether the tool-call ordering
 * survives the round trip: whether {@code sequence} is a stored property or an accident of
 * insertion order. The calls are therefore handed in as 3, 1, 2 and must come back as 1, 2, 3.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("FR15: briefing persistence")
class JpaBriefingStoreTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-10-04T18:00:00Z");
    private static final String BRIEFING_ID = "BRF-0A1B2C3D";

    @Autowired
    private BriefingJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("a briefing round-trips with its tool calls in ascending sequence order")
    void briefing_roundTripsWithToolCallsInAscendingSequenceOrder() {
        BriefingStore store = new JpaBriefingStore(repository);

        EndOfDayBriefing briefing = new EndOfDayBriefing(
                BRIEFING_ID,
                LocalDate.of(2026, 10, 4),
                GENERATED_AT,
                "Two batches remain at risk tonight. One markdown was recommended today.",
                2,
                1,
                List.of(
                        new ToolCall(3, "getSellThroughRate", "SKU-YOG-1KG",
                                "velocityPerHour=120.0", GENERATED_AT),
                        new ToolCall(1, "listCurrentRisks", "",
                                "batchId=BAT-WORST sku=SKU-YOG-1KG", GENERATED_AT),
                        new ToolCall(2, "listRecommendations", "2026-10-04",
                                "batchId=BAT-A finalRecommendation=PERCENT_30", GENERATED_AT)));

        store.save(briefing);
        entityManager.flush();
        entityManager.clear();

        Optional<EndOfDayBriefing> found = store.findById(BRIEFING_ID);

        assertThat(found).isPresent();
        EndOfDayBriefing reloaded = found.get();

        assertThat(reloaded.businessDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(reloaded.generatedAt()).isEqualTo(GENERATED_AT);
        assertThat(reloaded.summary()).isEqualTo(briefing.summary());
        assertThat(reloaded.riskCount()).isEqualTo(2);
        assertThat(reloaded.recommendationCount()).isEqualTo(1);

        assertThat(reloaded.toolCalls())
                .as("spec-04 declares sequence on every tool call, so the order must be a stored "
                        + "property and not an artefact of how the rows were inserted")
                .extracting(ToolCall::sequence)
                .containsExactly(1, 2, 3);
        assertThat(reloaded.toolCalls())
                .extracting(ToolCall::toolName)
                .containsExactly("listCurrentRisks", "listRecommendations", "getSellThroughRate");
        assertThat(reloaded.toolCalls())
                .extracting(ToolCall::arguments)
                .containsExactly("", "2026-10-04", "SKU-YOG-1KG");
    }

    @Test
    @DisplayName("an unknown identifier is empty, which the endpoint renders as 404")
    void findById_whenUnknown_isEmpty() {
        BriefingStore store = new JpaBriefingStore(repository);

        assertThat(store.findById("BRF-DOESNOTEXIST")).isEmpty();
    }
}
