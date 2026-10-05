package com.freshflow.insight.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application-layer signal that a model behind one of the two agent ports could not be used.
 * Written before the class existed (5 October 2026), after the live run of that afternoon found
 * that a Gemini quota error surfaced as a 500 from {@code POST /api/insight/briefings} and as a
 * forty-line stack trace from the FR14 consumer.
 *
 * <p>What the exception has to promise: a one-line, bounded {@code reason} that a log line and a
 * 503 body can carry, with the provider's exception class named so that a rate limit and a
 * timeout can be told apart, and with nothing that looks like a credential in it.
 */
@DisplayName("AgentUnavailableException: a bounded, one-line, credential-free reason")
class AgentUnavailableExceptionTest {

    @Test
    @DisplayName("the reason names the cause's class and collapses its message to one line")
    void reason_namesTheCauseAndCollapsesWhitespace() {
        RuntimeException cause = new IllegalStateException("{\n  \"error\": {\n    \"code\": 429,\n"
                + "    \"message\": \"You exceeded your current quota\"\n  }\n}");

        AgentUnavailableException e = new AgentUnavailableException("FR15 briefing agent", cause);

        assertThat(e.agent()).isEqualTo("FR15 briefing agent");
        assertThat(e.reason()).startsWith("IllegalStateException: ");
        assertThat(e.reason()).doesNotContain("\n").doesNotContain("  ");
        assertThat(e.reason()).contains("\"code\": 429");
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).contains("FR15 briefing agent").contains(e.reason());
    }

    @Test
    @DisplayName("the reason is bounded so a log line or a 503 body cannot carry a whole error page")
    void reason_isTruncated() {
        String longMessage = "x".repeat(2_000);

        AgentUnavailableException e = new AgentUnavailableException("FR14 markdown agent",
                new RuntimeException(longMessage));

        assertThat(e.reason().length()).isLessThanOrEqualTo(AgentUnavailableException.MAX_REASON_LENGTH);
        assertThat(e.reason()).endsWith("...");
    }

    @Test
    @DisplayName("a cause with no message still yields a usable reason")
    void reason_withNoMessage() {
        AgentUnavailableException e = new AgentUnavailableException("FR14 markdown agent",
                new RuntimeException((String) null));

        assertThat(e.reason()).isEqualTo("RuntimeException");
    }

    @Test
    @DisplayName("anything shaped like a key in a URL is redacted before it can reach a log or a body")
    void reason_redactsKeyLikeQueryParameters() {
        AgentUnavailableException e = new AgentUnavailableException("FR14 markdown agent",
                new RuntimeException("POST https://example.invalid/v1beta/models/x:generateContent"
                        + "?key=NOT-A-REAL-KEY-123 failed with 503"));

        assertThat(e.reason()).doesNotContain("NOT-A-REAL-KEY");
        assertThat(e.reason()).contains("key=***");
        assertThat(e.getMessage()).doesNotContain("NOT-A-REAL-KEY");
    }
}
