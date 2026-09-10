package com.freshflow.insight.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MVP tool set (spec-06 &sect;4, team ruling R4). Written BEFORE the implementation, so
 * <b>expected to FAIL</b>: {@code AgentToolRegistry}'s methods throw
 * {@code UnsupportedOperationException("FR14 Phase B")}.
 *
 * <p>A tool set is a security boundary as much as a feature list. Whatever the prompt says, an
 * agent can do exactly what its tools let it do and nothing else, so "which tools exist" is the
 * one FR14 question that is answerable mechanically rather than by reading a prompt template. That
 * is why it is asserted here rather than described in a document.
 */
@DisplayName("FR14: the agent tool registry (spec-06 s4)")
class AgentToolRegistryTest {

    private static final Instant NOW = Instant.parse("2026-09-10T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AgentToolRegistry tools = new AgentToolRegistry(
            new StubBatchDetailsProvider(),
            new VelocityQueryService(
                    (sku, from, to) -> List.of(),
                    sku -> OptionalDouble.empty(),
                    CLOCK,
                    60));

    /**
     * Exactly three, and the assertion is two-sided on purpose.
     *
     * <p>The first half says the MVP tools are all present. The second says the three later ones
     * are not, and that half is the one worth having: {@code getMarginFloor} would put the Pricing
     * context's invariant in front of a language model, which spec-01 &sect;2.5 forbids in terms,
     * and a registry that offered it would breach the architecture without breaking a single other
     * test.
     */
    @Test
    @DisplayName("the registry offers exactly the three MVP tools and no others")
    void toolNames_areExactlyTheThreeMvpTools() {
        assertThat(tools.toolNames())
                .containsExactlyInAnyOrder(
                        AgentToolRegistry.GET_BATCH_DETAILS,
                        AgentToolRegistry.GET_SELL_THROUGH_RATE,
                        AgentToolRegistry.PROPOSE_MARKDOWN)
                .as("spec-02 FR14 schedules these for Week 8 and Week 9; two of them are not "
                        + "merely later but architecturally out of bounds for this service")
                .doesNotContain("getMarkdownHistory", "getMarginFloor", "queryStorePolicy");
    }
}
