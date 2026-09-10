package com.freshflow.insight.application;

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
        throw new UnsupportedOperationException("FR14 Phase B");
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
        throw new UnsupportedOperationException("FR14 Phase B");
    }
}
