package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.BriefingToolRegistry;
import com.freshflow.insight.application.InMemoryAgentTraceStore;
import com.freshflow.insight.application.RiskQueryService;
import com.freshflow.insight.application.VelocityQueryService;
import com.freshflow.insight.application.WindowedQuantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool-argument encoding between the model and {@code BriefingToolRegistry}, pinned (FR15).
 *
 * <p>The same reasoning as {@code MarkdownToolArgumentsTest}: a wrong encoding fails silently in
 * production, with every call answered {@code error=…} and no test the wiser. Each case encodes
 * what a model would emit and then feeds the result to a genuine registry over stub ports, so the
 * two halves of the contract cannot drift together unnoticed. No LangChain4j type appears here.
 */
@DisplayName("FR15: the tool-call encoding between the model and BriefingToolRegistry")
class BriefingToolArgumentsTest {

    private static final Instant NOW = Instant.parse("2026-10-04T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SKU = "SKU-YOG-1KG";

    private final BriefingToolRegistry tools = new BriefingToolRegistry(
            new RiskQueryService(List::of),
            new InMemoryAgentTraceStore(),
            velocityQueries());

    private static VelocityQueryService velocityQueries() {
        List<WindowedQuantity> windows = List.of(new WindowedQuantity(NOW.minusSeconds(60), NOW, 2));
        return new VelocityQueryService(
                (sku, from, to) -> SKU.equals(sku) ? windows : List.of(),
                sku -> OptionalDouble.empty(),
                CLOCK,
                60);
    }

    @Test
    @DisplayName("listCurrentRisks takes nothing, so it encodes to the empty string")
    void encode_listCurrentRisks_isEmptyWhateverTheModelSends() {
        assertThat(BriefingToolArguments.encode(BriefingToolRegistry.LIST_CURRENT_RISKS, "{}"))
                .isEmpty();
        assertThat(BriefingToolArguments.encode(BriefingToolRegistry.LIST_CURRENT_RISKS, null))
                .isEmpty();
        assertThat(tools.invoke(BriefingToolRegistry.LIST_CURRENT_RISKS, ""))
                .doesNotStartWith("error=");
    }

    @Test
    @DisplayName("listRecommendations' businessDate becomes a bare ISO date the registry accepts")
    void encode_listRecommendations_producesABareDate() {
        String arguments = BriefingToolArguments.encode(BriefingToolRegistry.LIST_RECOMMENDATIONS,
                "{\"businessDate\":\"2026-10-04\"}");

        assertThat(arguments).isEqualTo("2026-10-04");
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, arguments))
                .doesNotStartWith("error=");
    }

    @Test
    @DisplayName("getSellThroughRate's sku becomes a bare token the registry accepts")
    void encode_getSellThroughRate_producesABareSku() {
        String arguments = BriefingToolArguments.encode(BriefingToolRegistry.GET_SELL_THROUGH_RATE,
                "{\"sku\":\"" + SKU + "\"}");

        assertThat(arguments).isEqualTo(SKU);
        assertThat(tools.invoke(BriefingToolRegistry.GET_SELL_THROUGH_RATE, arguments))
                .contains("velocityPerHour=120.0");
    }

    /** The encoder's job on bad input is to hand the registry something it will reject, not to throw. */
    @Test
    @DisplayName("malformed or missing arguments encode to something the registry rejects, not to an exception")
    void encode_whenArgumentsAreMalformed_yieldsARejectableStringRatherThanThrowing() {
        String missing = BriefingToolArguments.encode(BriefingToolRegistry.LIST_RECOMMENDATIONS, "{}");
        String garbage = BriefingToolArguments.encode(BriefingToolRegistry.GET_SELL_THROUGH_RATE,
                "not json");

        assertThat(missing).isEmpty();
        assertThat(garbage).isEmpty();
        assertThat(tools.invoke(BriefingToolRegistry.LIST_RECOMMENDATIONS, missing))
                .startsWith("error=INVALID_ARGUMENTS");
        assertThat(tools.invoke(BriefingToolRegistry.GET_SELL_THROUGH_RATE, garbage))
                .startsWith("error=INVALID_ARGUMENTS");
    }

    @Test
    @DisplayName("a tool name the registry does not know is passed through, not swallowed")
    void encode_unknownToolName_passesTheArgumentsThrough() {
        String arguments = BriefingToolArguments.encode("proposeMarkdown", "{\"batchId\":\"X\"}");

        assertThat(arguments).isEqualTo("{\"batchId\":\"X\"}");
        assertThat(tools.invoke("proposeMarkdown", arguments))
                .startsWith("error=UNKNOWN_TOOL");
    }
}
