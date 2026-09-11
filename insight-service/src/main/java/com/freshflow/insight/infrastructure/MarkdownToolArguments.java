package com.freshflow.insight.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshflow.contracts.DiscountTier;
import com.freshflow.insight.application.AgentToolRegistry;
import com.freshflow.insight.application.MarkdownProposal;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Translates a model's JSON tool arguments into the encoding {@link AgentToolRegistry#invoke} reads,
 * and back again (FR14).
 *
 * <p><b>This class is a contract between two pieces of code that never meet in a test, which is why
 * it exists as a class at all.</b> A language model emits tool arguments as a JSON object;
 * {@code AgentToolRegistry} reads a flat string. The shape of that string was fixed in Phase B1 and
 * recorded as assumption B1-A3, and it is load-bearing: get it wrong and every call answers
 * {@code error=INVALID_ARGUMENTS}, the model loops, and nothing whatever fails loudly. Phase B1's
 * log named it "asserted by no test". {@code MarkdownToolArgumentsTest} is that assertion.
 *
 * <p>The encoding, in full:
 *
 * <table>
 *   <caption>Tool argument encoding</caption>
 *   <tr><th>Tool</th><th>JSON the model emits</th><th>What the registry is given</th></tr>
 *   <tr><td>{@code getBatchDetails}</td><td>{@code {"batchId":"BAT-1"}}</td><td>{@code BAT-1}</td></tr>
 *   <tr><td>{@code getSellThroughRate}</td><td>{@code {"sku":"SKU-1"}}</td><td>{@code SKU-1}</td></tr>
 *   <tr><td>{@code proposeMarkdown}</td><td>four properties</td>
 *       <td>{@code batchId,tier,effectiveFrom,rationale}</td></tr>
 * </table>
 *
 * <p><b>Why the rationale goes last, and why it is not escaped.</b> The registry splits on a comma
 * with a limit of four, so everything after the third comma is the rationale &mdash; a written
 * justification containing commas therefore survives intact, which a naive split would not manage
 * and an escaping scheme would only manage by inventing one. Putting the free text last is what
 * makes the absence of escaping safe rather than lucky.
 *
 * <p><b>Nothing here throws.</b> Malformed JSON, a missing property and a wrong type all produce
 * the best encoding available, which the registry then rejects with {@code error=INVALID_ARGUMENTS}
 * &mdash; a tool failure the model is told about and may correct. That is the ReAct pattern working.
 * An exception here would end the loop instead, and would do so on the one input a language model
 * is most likely to get wrong.
 *
 * <p>Package-private and static: it is infrastructure's private translation layer, not a type the
 * application layer knows about. It imports Jackson, which is why it lives in
 * {@code infrastructure} and not beside the registry (spec-01 &sect;2.2).
 */
final class MarkdownToolArguments {

    /** The single argument of {@code getBatchDetails}, and the first of {@code proposeMarkdown}. */
    static final String BATCH_ID = "batchId";

    /** The single argument of {@code getSellThroughRate}. */
    static final String SKU = "sku";

    /** {@code proposeMarkdown}: one of the four {@link DiscountTier} names. */
    static final String TIER = "tier";

    /** {@code proposeMarkdown}: an ISO-8601 instant. */
    static final String EFFECTIVE_FROM = "effectiveFrom";

    /** {@code proposeMarkdown}: FR14's written justification. Last, so it may contain commas. */
    static final String RATIONALE = "rationale";

    private static final ObjectMapper JSON = new ObjectMapper();

    private MarkdownToolArguments() {
    }

    /**
     * The arguments string {@code AgentToolRegistry.invoke} is to be given for this call.
     *
     * @param toolName      the tool the model asked for; an unknown name yields the raw JSON, which
     *                      the registry answers with {@code error=UNKNOWN_TOOL}
     * @param jsonArguments the arguments object the model emitted, as text; may be null or malformed
     * @return the encoded arguments, never null
     */
    static String encode(String toolName, String jsonArguments) {
        JsonNode arguments = parse(jsonArguments);

        if (AgentToolRegistry.GET_BATCH_DETAILS.equals(toolName)) {
            return text(arguments, BATCH_ID);
        }
        if (AgentToolRegistry.GET_SELL_THROUGH_RATE.equals(toolName)) {
            return text(arguments, SKU);
        }
        if (AgentToolRegistry.PROPOSE_MARKDOWN.equals(toolName)) {
            // The rationale is last on purpose: the registry splits with a limit of four, so a
            // justification containing commas arrives whole.
            return text(arguments, BATCH_ID)
                    + "," + text(arguments, TIER)
                    + "," + text(arguments, EFFECTIVE_FROM)
                    + "," + text(arguments, RATIONALE);
        }
        return jsonArguments == null ? "" : jsonArguments;
    }

    /**
     * The proposal a {@code proposeMarkdown} call carries, or {@code null} if it does not carry a
     * well-formed one.
     *
     * <p>Called only after the registry has accepted the call, so this parse is a re-read of
     * arguments already validated rather than a second, weaker validation. It still returns
     * {@code null} rather than throwing, because a decode that disagreed with the registry should
     * decline to produce a proposal rather than take down the deliberation.
     *
     * @param jsonArguments the arguments object the model emitted, as text
     * @return the proposal, or {@code null}
     */
    static MarkdownProposal decodeProposal(String jsonArguments) {
        JsonNode arguments = parse(jsonArguments);

        String batchId = text(arguments, BATCH_ID);
        String rationale = text(arguments, RATIONALE);
        if (batchId.isEmpty() || rationale.isEmpty()) {
            return null;
        }

        DiscountTier tier;
        try {
            tier = DiscountTier.valueOf(text(arguments, TIER));
        } catch (IllegalArgumentException e) {
            return null;
        }

        Instant effectiveFrom;
        try {
            effectiveFrom = Instant.parse(text(arguments, EFFECTIVE_FROM));
        } catch (DateTimeParseException e) {
            return null;
        }

        return new MarkdownProposal(batchId, tier, effectiveFrom, rationale);
    }

    // --------------------------------------------------------------------------------- helpers

    private static JsonNode parse(String jsonArguments) {
        if (jsonArguments == null || jsonArguments.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = JSON.readTree(jsonArguments);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    /** A property as plain text, or the empty string. A number or boolean is rendered, not refused. */
    private static String text(JsonNode arguments, String property) {
        if (arguments == null) {
            return "";
        }
        JsonNode value = arguments.get(property);
        if (value == null || value.isNull()) {
            return "";
        }
        return (value.isValueNode() ? value.asText() : value.toString()).trim();
    }
}
