package com.freshflow.insight.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.freshflow.insight.application.BriefingToolRegistry;

/**
 * Translates a model's JSON tool arguments into the encoding {@code BriefingToolRegistry.invoke}
 * reads (FR15) &mdash; the same contract {@link MarkdownToolArguments} is for FR14, for the same
 * reason: the registry reads a flat string, the model emits JSON, and if the translation is wrong
 * every call answers {@code error=…}, the model loops, and nothing fails loudly.
 *
 * <p>The encoding, in full. Every briefing tool takes at most one argument, so there is no
 * multi-part form and no decode direction:
 *
 * <table>
 *   <caption>Tool argument encoding</caption>
 *   <tr><th>Tool</th><th>JSON the model emits</th><th>What the registry is given</th></tr>
 *   <tr><td>{@code listCurrentRisks}</td><td>{@code {}} or nothing</td><td>the empty string</td></tr>
 *   <tr><td>{@code listRecommendations}</td><td>{@code {"businessDate":"2026-10-04"}}</td>
 *       <td>{@code 2026-10-04}</td></tr>
 *   <tr><td>{@code getSellThroughRate}</td><td>{@code {"sku":"SKU-1"}}</td><td>{@code SKU-1}</td></tr>
 * </table>
 *
 * <p><b>Nothing here throws.</b> Malformed JSON and a missing property produce the empty string,
 * which the registry rejects with {@code error=INVALID_ARGUMENTS} &mdash; a tool failure the model
 * is told about and may correct. An unknown tool name passes the raw JSON through, which the
 * registry answers with {@code error=UNKNOWN_TOOL}.
 *
 * <p>Package-private and static, and it shares {@link MarkdownToolArguments}' JSON helpers rather
 * than owning a second parser: the "never throws" guarantee is worth having in one place.
 */
final class BriefingToolArguments {

    /** The single argument of {@code listRecommendations}: an ISO-8601 date. */
    static final String BUSINESS_DATE = "businessDate";

    /** The single argument of {@code getSellThroughRate}, named as FR14 names it. */
    static final String SKU = MarkdownToolArguments.SKU;

    private BriefingToolArguments() {
    }

    /**
     * The arguments string {@code BriefingToolRegistry.invoke} is to be given for this call.
     *
     * @param toolName      the tool the model asked for
     * @param jsonArguments the arguments object the model emitted, as text; may be null or malformed
     * @return the encoded arguments, never null
     */
    static String encode(String toolName, String jsonArguments) {
        if (BriefingToolRegistry.LIST_CURRENT_RISKS.equals(toolName)) {
            return "";
        }

        JsonNode arguments = MarkdownToolArguments.parse(jsonArguments);
        if (BriefingToolRegistry.LIST_RECOMMENDATIONS.equals(toolName)) {
            return MarkdownToolArguments.text(arguments, BUSINESS_DATE);
        }
        if (BriefingToolRegistry.GET_SELL_THROUGH_RATE.equals(toolName)) {
            return MarkdownToolArguments.text(arguments, SKU);
        }
        return jsonArguments == null ? "" : jsonArguments;
    }
}
