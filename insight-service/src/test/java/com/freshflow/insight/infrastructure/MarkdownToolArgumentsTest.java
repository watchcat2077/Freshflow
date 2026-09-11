package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.insight.application.AgentToolRegistry;
import com.freshflow.insight.application.BatchDetails;
import com.freshflow.insight.application.MarkdownProposal;
import com.freshflow.insight.application.StubBatchDetailsProvider;
import com.freshflow.insight.application.VelocityQueryService;
import com.freshflow.insight.application.WindowedQuantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool-argument encoding, pinned (FR14, Phase B2).
 *
 * <p><b>Why this test exists.</b> Phase B1 implemented {@code AgentToolRegistry.invoke} against a
 * flat argument string &mdash; {@code batchId,tier,effectiveFrom,rationale} for
 * {@code proposeMarkdown}, a single bare token for the two read tools &mdash; and recorded the
 * encoding as assumption B1-A3 with the note that it is <i>load-bearing and asserted by no test</i>.
 * A language model emits JSON. Something has to translate, and if the translation is wrong every
 * call comes back {@code error=INVALID_ARGUMENTS}, the model loops until it gives up, and
 * <b>nothing fails</b>: the run produces a trace full of rejected calls and no recommendation, and
 * every test in the suite still passes. That is the shape of defect a test has to catch, because
 * nobody will catch it by reading.
 *
 * <p><b>It asserts the encoder against the real registry, not against a restatement of the rule.</b>
 * Each case below encodes what a model would emit and then feeds the result to a genuine
 * {@link AgentToolRegistry} over stub ports. A test that only compared the encoder's output to a
 * literal would pass just as happily if both halves of the contract drifted together.
 *
 * <p><b>No LangChain4j type appears here.</b> The encoder is deliberately a separate class from
 * {@code LangChain4jMarkdownDecisionAgent} so that the contract can be asserted without one, which
 * keeps ADR 0004's second verification check &mdash; no test source references the model library
 * &mdash; true after Phase B2 as it was before it. What this test does not cover is the one line in
 * the adapter that calls the encoder; that line is exercised for the first time in the B3 run.
 */
@DisplayName("FR14: the tool-call encoding between the model and AgentToolRegistry (B1-A3)")
class MarkdownToolArgumentsTest {

    private static final Instant NOW = Instant.parse("2026-09-10T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String BATCH_ID = "BAT-0001";
    private static final String SKU = "SKU-YOG-1KG";
    private static final Instant EXPIRY_AT = NOW.plus(Duration.ofHours(9));

    private final AgentToolRegistry tools = new AgentToolRegistry(
            new StubBatchDetailsProvider().with(new BatchDetails(BATCH_ID, SKU, 41, EXPIRY_AT,
                    new MoneyPayload(new BigDecimal("2.10"), "AUD"))),
            velocityQueries());

    private static VelocityQueryService velocityQueries() {
        List<WindowedQuantity> windows = List.of(new WindowedQuantity(NOW.minusSeconds(60), NOW, 2));
        return new VelocityQueryService(
                (sku, from, to) -> SKU.equals(sku) ? windows : List.of(),
                sku -> SKU.equals(sku) ? OptionalDouble.of(120.0) : OptionalDouble.empty(),
                CLOCK,
                60);
    }

    // ------------------------------------------------------------------------ the two read tools

    @Test
    @DisplayName("a read tool's single property becomes a bare token the registry accepts")
    void encode_singleArgumentTools_produceABareToken() {
        String batchArguments =
                MarkdownToolArguments.encode(AgentToolRegistry.GET_BATCH_DETAILS,
                        "{\"batchId\":\"" + BATCH_ID + "\"}");
        String skuArguments =
                MarkdownToolArguments.encode(AgentToolRegistry.GET_SELL_THROUGH_RATE,
                        "{\"sku\":\"" + SKU + "\"}");

        assertThat(batchArguments)
                .as("B1-A3: the read tools take one bare token, not a JSON object")
                .isEqualTo(BATCH_ID);
        assertThat(skuArguments).isEqualTo(SKU);

        assertThat(tools.invoke(AgentToolRegistry.GET_BATCH_DETAILS, batchArguments))
                .doesNotStartWith("error=")
                .contains("batchId=" + BATCH_ID)
                .contains("unitCost=2.10 AUD");
        assertThat(tools.invoke(AgentToolRegistry.GET_SELL_THROUGH_RATE, skuArguments))
                .doesNotStartWith("error=")
                .contains("velocityPerHour=");
    }

    // --------------------------------------------------------------------------- proposeMarkdown

    /**
     * The order of the four parts is the contract, and the rationale is last for a reason: the
     * registry splits on a comma with a limit of four, so a written justification containing
     * commas &mdash; which a real one will &mdash; arrives whole. Put the rationale anywhere else
     * and the first comma in it silently truncates the proposal.
     */
    @Test
    @DisplayName("proposeMarkdown encodes as batchId,tier,effectiveFrom,rationale in that order")
    void encode_proposeMarkdown_producesTheFourPartOrderTheRegistrySplitsOn() {
        String arguments = MarkdownToolArguments.encode(AgentToolRegistry.PROPOSE_MARKDOWN, json());

        assertThat(arguments).isEqualTo(
                BATCH_ID + ",PERCENT_30,2026-09-10T09:30:00Z," + RATIONALE);

        assertThat(tools.invoke(AgentToolRegistry.PROPOSE_MARKDOWN, arguments))
                .as("the registry must accept what the encoder produced; a mismatch here is the "
                        + "defect this whole test exists for, and it fails silently in production")
                .isEqualTo("recorded batchId=" + BATCH_ID + " tier=PERCENT_30");
    }

    @Test
    @DisplayName("a rationale containing commas survives the round trip intact")
    void encode_proposeMarkdown_whenRationaleContainsCommas_keepsItWhole() {
        String commas = "41 units remain, 9 hours to expiry, and the rate is 2.0/h.";
        String arguments = MarkdownToolArguments.encode(AgentToolRegistry.PROPOSE_MARKDOWN,
                json(BATCH_ID, "PERCENT_30", "2026-09-10T09:30:00Z", commas));

        assertThat(tools.invoke(AgentToolRegistry.PROPOSE_MARKDOWN, arguments))
                .doesNotStartWith("error=");
        assertThat(MarkdownToolArguments.decodeProposal(
                json(BATCH_ID, "PERCENT_30", "2026-09-10T09:30:00Z", commas)).rationale())
                .as("the rationale is the published event's rationale; truncating it at the first "
                        + "comma would publish half a justification")
                .isEqualTo(commas);
    }

    @Test
    @DisplayName("the decoded proposal is the one the model stated, field for field")
    void decodeProposal_returnsWhatTheModelProposed() {
        MarkdownProposal proposal = MarkdownToolArguments.decodeProposal(json());

        assertThat(proposal).isNotNull();
        assertThat(proposal.batchId()).isEqualTo(BATCH_ID);
        assertThat(proposal.discountTier()).isEqualTo(DiscountTier.PERCENT_30);
        assertThat(proposal.effectiveFrom()).isEqualTo(NOW);
        assertThat(proposal.rationale()).isEqualTo(RATIONALE);
    }

    // -------------------------------------------------------------------------- malformed input

    /**
     * A model gets tool arguments wrong. The encoder's job when that happens is to hand the
     * registry something it will reject with {@code error=INVALID_ARGUMENTS}, which the agent is
     * told about and may correct &mdash; not to throw, which would end the ReAct loop on the one
     * input a language model is most likely to get wrong.
     */
    @Test
    @DisplayName("malformed or missing arguments encode to something the registry rejects, not to an exception")
    void encode_whenArgumentsAreMalformed_yieldsARejectableStringRatherThanThrowing() {
        assertThat(MarkdownToolArguments.encode(AgentToolRegistry.GET_BATCH_DETAILS, "not json"))
                .isEmpty();
        assertThat(MarkdownToolArguments.encode(AgentToolRegistry.GET_BATCH_DETAILS, null))
                .isEmpty();
        assertThat(MarkdownToolArguments.encode(AgentToolRegistry.GET_SELL_THROUGH_RATE, "{}"))
                .isEmpty();

        assertThat(tools.invoke(AgentToolRegistry.GET_BATCH_DETAILS,
                MarkdownToolArguments.encode(AgentToolRegistry.GET_BATCH_DETAILS, "{}")))
                .startsWith("error=INVALID_ARGUMENTS");

        String missingTier = MarkdownToolArguments.encode(AgentToolRegistry.PROPOSE_MARKDOWN,
                "{\"batchId\":\"" + BATCH_ID + "\",\"effectiveFrom\":\"2026-09-10T09:30:00Z\","
                        + "\"rationale\":\"because\"}");
        assertThat(missingTier)
                .as("every part is still present, so the registry can say WHICH one is wrong")
                .isEqualTo(BATCH_ID + ",,2026-09-10T09:30:00Z,because");
        assertThat(tools.invoke(AgentToolRegistry.PROPOSE_MARKDOWN, missingTier))
                .startsWith("error=INVALID_ARGUMENTS");

        assertThat(MarkdownToolArguments.decodeProposal("{\"batchId\":\"" + BATCH_ID + "\"}"))
                .as("an incomplete proposal decodes to nothing rather than to a partial one")
                .isNull();
    }

    @Test
    @DisplayName("a tool name the registry does not know is passed through, not swallowed")
    void encode_unknownToolName_passesTheArgumentsThrough() {
        String arguments = MarkdownToolArguments.encode("getMarkdownHistory", "{\"sku\":\"X\"}");

        assertThat(arguments).isEqualTo("{\"sku\":\"X\"}");
        assertThat(tools.invoke("getMarkdownHistory", arguments))
                .as("R4: getMarkdownHistory is one of the three later tools that appear nowhere. The name "
                        + "is answered, so the agent learns it does not exist rather than being cut off")
                .startsWith("error=UNKNOWN_TOOL");
    }

    // --------------------------------------------------------------------------------- fixtures

    private static final String RATIONALE =
            "Remaining quantity will not clear before expiry at the current sell-through rate.";

    private static String json() {
        return json(BATCH_ID, "PERCENT_30", "2026-09-10T09:30:00Z", RATIONALE);
    }

    private static String json(String batchId, String tier, String effectiveFrom,
                               String rationale) {
        return "{\"batchId\":\"" + batchId + "\",\"tier\":\"" + tier + "\","
                + "\"effectiveFrom\":\"" + effectiveFrom + "\",\"rationale\":\"" + rationale
                + "\"}";
    }
}
