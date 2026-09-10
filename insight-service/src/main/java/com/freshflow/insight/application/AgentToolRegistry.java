package com.freshflow.insight.application;

import com.freshflow.contracts.DiscountTier;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * The tools FR14's agent may call, and the only ones it may call.
 *
 * <p><b>Three, exactly.</b> {@code getBatchDetails}, {@code getSellThroughRate} and
 * {@code proposeMarkdown}. spec-02 FR14's table lists six; the other three &mdash;
 * {@code getMarkdownHistory}, {@code getMarginFloor} and {@code queryStorePolicy} &mdash; are later
 * work and are absent from this class, from the prompt the adapter builds and from every test, not
 * merely disabled. A registry that knows the name of a tool it will not run is a registry that will
 * eventually run it.
 *
 * <p>Two of those three absences are not schedule decisions. {@code getMarginFloor} would put the
 * Pricing context's invariant in front of a language model, which spec-01 &sect;2.5 forbids in
 * terms, and {@code queryStorePolicy} needs retrieval infrastructure no specification has approved.
 * They are listed in spec-02 for Week 9 and they stay there.
 *
 * <p><b>Why a class and not an interface.</b> There is nothing to substitute. Its two dependencies
 * are already ports &mdash; {@link BatchDetailsProvider} and, for the velocity tool, the existing
 * {@link VelocityQueryService} and the ports beneath it &mdash; so every test that needs a
 * different answer from a tool supplies a different port, not a different registry. Adding an
 * interface here would abstract over an abstraction.
 *
 * <p>It takes its collaborators as constructor arguments and reads no configuration, so a test
 * constructs one in a line without a Spring context. Its bean method is Phase B work, and will sit
 * in {@code infrastructure} beside {@code RiskPolicyConfiguration}, which does the same job for
 * {@code RiskAssessmentPolicy}.
 *
 * <p>The tool contracts &mdash; argument list, return shape and what each answers when the data is
 * absent &mdash; are specified in spec-06 &sect;4.
 */
public class AgentToolRegistry {

    /** spec-02 FR14: returns remaining quantity, expiry timestamp and unit cost for one batch. */
    public static final String GET_BATCH_DETAILS = "getBatchDetails";

    /** spec-02 FR14: returns the current velocity and, where one is stored, the baseline ratio. */
    public static final String GET_SELL_THROUGH_RATE = "getSellThroughRate";

    /** spec-02 FR14: states the recommendation. It records a proposal; it does not set a price. */
    public static final String PROPOSE_MARKDOWN = "proposeMarkdown";

    private final BatchDetailsProvider batchDetailsProvider;
    private final VelocityQueryService velocityQueryService;

    public AgentToolRegistry(BatchDetailsProvider batchDetailsProvider,
                             VelocityQueryService velocityQueryService) {
        this.batchDetailsProvider = batchDetailsProvider;
        this.velocityQueryService = velocityQueryService;
    }

    /**
     * The names the agent may invoke. Exactly the three constants above, and nothing else.
     *
     * @return the tool names offered to the agent
     */
    public Set<String> toolNames() {
        return Set.of(GET_BATCH_DETAILS, GET_SELL_THROUGH_RATE, PROPOSE_MARKDOWN);
    }

    /**
     * Runs one tool and returns what the agent is told, verbatim, for the trace to record.
     *
     * <p>The result is a {@code String} because spec-04 types {@code ToolCall.result} as one and
     * because it is what a ReAct loop actually hands back to a model. An unknown name, or arguments
     * a tool cannot parse, are answered as tool failures rather than raised as exceptions: a model
     * that mis-calls a tool should be told so and given the chance to try again, which is the whole
     * mechanism of the pattern. spec-06 &sect;4 fixes the wording of each answer.
     *
     * @param toolName  one of the three names above
     * @param arguments the call's arguments, as the agent supplied them
     * @return the result text the agent sees, and the trace records
     */
    public String invoke(String toolName, String arguments) {
        if (toolName == null) {
            return "error=UNKNOWN_TOOL toolName=";
        }
        return switch (toolName) {
            case GET_BATCH_DETAILS -> getBatchDetails(arguments);
            case GET_SELL_THROUGH_RATE -> getSellThroughRate(arguments);
            case PROPOSE_MARKDOWN -> proposeMarkdown(arguments);
            default -> "error=UNKNOWN_TOOL toolName=" + toolName;
        };
    }

    // ------------------------------------------------------------------------------- the tools

    /**
     * spec-06 &sect;4.1. An unknown batch is reported, not raised: the port promises an empty
     * result for a batch it cannot see, and turning that into an exception would end the ReAct
     * loop instead of letting the agent correct itself.
     */
    private String getBatchDetails(String arguments) {
        String batchId = argument(arguments);
        if (batchId == null) {
            return "error=INVALID_ARGUMENTS " + GET_BATCH_DETAILS + " expects batchId";
        }
        return batchDetailsProvider.findBatch(batchId)
                .map(AgentToolRegistry::render)
                .orElse("error=BATCH_NOT_FOUND batchId=" + batchId);
    }

    /**
     * spec-06 &sect;4.2. An empty result is reported as unknown and never as zero: FR12's empty
     * {@code Optional} covers "no window has closed yet" and "the newest closed window has aged
     * out", and neither of those is a measurement of nothing selling. The baseline fields are
     * omitted when no baseline is stored, for the same reason spec-01 &sect;2.7 omits them from
     * the endpoint.
     */
    private String getSellThroughRate(String arguments) {
        String sku = argument(arguments);
        if (sku == null) {
            return "error=INVALID_ARGUMENTS " + GET_SELL_THROUGH_RATE + " expects sku";
        }
        return velocityQueryService.findCurrentVelocity(sku)
                .map(AgentToolRegistry::render)
                .orElse("error=NO_CURRENT_VELOCITY sku=" + sku);
    }

    /**
     * spec-06 &sect;4.3 &mdash; it records a proposal and publishes nothing. The deliberation
     * carries the proposal back to {@link MarkdownAgentOrchestrator}, which publishes once per
     * trace; how many times a model calls this tool must not decide how many events cross into
     * another bounded context (spec-06 &sect;5).
     *
     * <p>The arguments are {@code batchId,tier,effectiveFrom,rationale}, split into four parts so
     * that a rationale containing a comma survives intact. Every rejection is a tool failure the
     * agent may correct rather than an exception.
     */
    private String proposeMarkdown(String arguments) {
        String[] parts = arguments == null ? new String[0] : arguments.split(",", 4);
        if (parts.length < 4) {
            return rejected(PROPOSE_MARKDOWN + " expects batchId,tier,effectiveFrom,rationale");
        }

        String batchId = parts[0].trim();
        String tier = parts[1].trim();
        String effectiveFrom = parts[2].trim();
        String rationale = parts[3].trim();

        if (batchId.isEmpty() || batchDetailsProvider.findBatch(batchId).isEmpty()) {
            return rejected("batchId=" + batchId + " is not a batch this service can see");
        }

        DiscountTier discountTier;
        try {
            discountTier = DiscountTier.valueOf(tier);
        } catch (IllegalArgumentException e) {
            return rejected("tier=" + tier + " is not one of the four tiers in the contract");
        }

        try {
            Instant.parse(effectiveFrom);
        } catch (DateTimeParseException e) {
            return rejected("effectiveFrom=" + effectiveFrom + " is not an ISO-8601 instant");
        }

        if (rationale.isEmpty()) {
            return rejected("rationale must not be blank");
        }

        return "recorded batchId=" + batchId + " tier=" + discountTier;
    }

    // --------------------------------------------------------------------------------- helpers

    private static String render(BatchDetails batch) {
        return "batchId=" + batch.batchId()
                + " sku=" + batch.sku()
                + " remainingQuantity=" + batch.remainingQuantity()
                + " expiryAt=" + batch.expiryAt()
                + " unitCost=" + batch.unitCost().amount().toPlainString()
                + " " + batch.unitCost().currency();
    }

    private static String render(SkuVelocityView velocity) {
        StringBuilder rendered = new StringBuilder("velocityPerHour=")
                .append(velocity.velocityPerHour());
        if (velocity.baselineVelocity() != null && velocity.velocityRatio() != null) {
            rendered.append(" baselineVelocity=").append(velocity.baselineVelocity())
                    .append(" velocityRatio=").append(velocity.velocityRatio());
        }
        return rendered.toString();
    }

    private static String rejected(String because) {
        return "error=INVALID_ARGUMENTS " + because;
    }

    /** The single-argument tools take one non-blank token; anything else is a tool failure. */
    private static String argument(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        return arguments.trim();
    }
}
