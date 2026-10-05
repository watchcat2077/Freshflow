package com.freshflow.insight.application;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The model behind an agent port could not be used &mdash; a rate limit, a timeout, an
 * authentication failure, a provider outage &mdash; so the agent did not deliberate at all.
 *
 * <p><b>Why it exists.</b> The live run of 5 October 2026 spent the model's daily free quota;
 * the provider answered 429 and the provider's own exception travelled unchanged out of the
 * adapters: {@code POST /api/insight/briefings} answered 500 with a stack trace, and the FR14
 * consumer logged forty lines per trigger. The application layer had no word for "the model is
 * down", so neither the controller nor the consumer could say it. This class is that word.
 *
 * <p><b>Where it sits, and why.</b> It is part of the two ports' contract
 * ({@link MarkdownDecisionAgent}, {@link BriefingAgent}): an adapter throws it, and nothing else
 * does. The application layer therefore depends on no provider type, which ADR 0004 requires,
 * and the two callers that need to react can do so in their own terms:
 *
 * <ul>
 *   <li>{@code BriefingController} maps it to 503 {@code AGENT_UNAVAILABLE} &mdash; the second
 *       of the two answers spec-04 gives that endpoint, distinguished from
 *       {@code AGENT_NOT_CONFIGURED} so that a caller can tell "no key" from "key, but the
 *       provider is refusing us right now".</li>
 *   <li>The FR14 trigger consumer logs one line, {@code [fr14-agent-unavailable]}, and lets the
 *       exception propagate so that the binder's retry policy, not this code, decides whether to
 *       try again. The trigger is not acknowledged as decided when it was not decided.</li>
 * </ul>
 *
 * <p><b>What the orchestrators do with it: nothing.</b> They let it through. A trace or a
 * briefing is the audit record of a decision, and an outage is not a decision; recording "the
 * model was unreachable" as a trace would put an operational failure into the business record
 * permanently (spec-06 &sect;5). That is the behaviour both orchestrator tests pin.
 *
 * <p><b>The reason is bounded and credential-free.</b> {@link #reason()} is one line, at most
 * {@link #MAX_REASON_LENGTH} characters, begins with the provider exception's class name so a
 * rate limit and a timeout read differently in a log, and has anything shaped like a {@code key=}
 * query parameter redacted. The provider's error bodies have never been seen to echo a key, but
 * the message reaches a log line and an HTTP body, and spec-01 &sect;2.7's "never logged" is a
 * promise this class would rather keep by construction than by assumption.
 */
public class AgentUnavailableException extends RuntimeException {

    /** Long enough for a provider's JSON error summary, short enough for one log line. */
    public static final int MAX_REASON_LENGTH = 240;

    private static final Pattern KEY_PARAMETER = Pattern.compile("(?i)(key=)[^&\\s\"']+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final String agent;
    private final String reason;

    /**
     * @param agent which agent could not be used, in words a log reader knows, for example
     *              {@code "FR14 markdown agent"}
     * @param cause the provider's exception, kept as the cause for the full stack in a debug log
     */
    public AgentUnavailableException(String agent, Throwable cause) {
        super(Objects.requireNonNull(agent, "agent") + " could not use its model: "
                + reasonOf(Objects.requireNonNull(cause, "cause")), cause);
        this.agent = agent;
        this.reason = reasonOf(cause);
    }

    /** Which agent could not be used. */
    public String agent() {
        return agent;
    }

    /** One bounded line: the provider exception's class, then its message collapsed and redacted. */
    public String reason() {
        return reason;
    }

    /** The one-line summary {@link #reason()} is built from; public so a test can pin its shape. */
    public static String reasonOf(Throwable cause) {
        String name = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return name;
        }
        String oneLine = WHITESPACE.matcher(message).replaceAll(" ").trim();
        String redacted = KEY_PARAMETER.matcher(oneLine).replaceAll("$1***");
        String summary = name + ": " + redacted;
        if (summary.length() > MAX_REASON_LENGTH) {
            summary = summary.substring(0, MAX_REASON_LENGTH - 3) + "...";
        }
        return summary;
    }
}
