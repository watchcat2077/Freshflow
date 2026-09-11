package com.freshflow.insight.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The LLM configuration spec-01 &sect;2.7 requires to be injected through {@code application.yml}
 * (FR14, ADR 0004).
 *
 * <p>The clause it implements is short and categorical: "LLM provider MUST be injected through
 * {@code application.yml}, so hosted API and local Ollama are interchangeable without code
 * change", and "API keys MUST come from environment variables. NEVER commit a key."
 *
 * <p><b>Nothing here has a hard-coded default that could become a secret.</b>
 * {@code api-key} and {@code model} arrive from {@code ${LLM_API_KEY}} and {@code ${LLM_MODEL}}
 * and default to empty; an empty {@code api-key} is the signal that FR14 is not configured on this
 * machine, and {@code MarkdownAgentConfiguration} then declines to build a model at all rather
 * than building one that would fail on its first call. The key is never logged and never written
 * to a file.
 *
 * <p>{@code provider} is carried for the reason spec-01 &sect;2.7 gives rather than for the reason
 * a switch statement would: it names which adapter is in force, and the interchangeability the
 * clause claims is delivered by the {@code MarkdownDecisionAgent} port (ADR 0004), not by a
 * branch. Pointing this service at a local Ollama is a new adapter behind the same port plus a
 * value here, and no application-layer code moves.
 */
@ConfigurationProperties(prefix = "freshflow.llm")
public class LlmProperties {

    /** Which provider adapter is in force. Recorded in spec-01 s2.7. */
    private String provider = "";

    /** The model name. From ${LLM_MODEL}; empty means "not configured on this machine". */
    private String model = "";

    /** The credential. From ${LLM_API_KEY} only, per spec-01 s2.7. Never logged, never committed. */
    private String apiKey = "";

    /** How long one model call may take before it is abandoned. */
    private Duration timeout = Duration.ofSeconds(60);

    /**
     * The most model round trips one deliberation may make before it is stopped.
     *
     * <p>A ReAct loop that never concludes is the failure mode this bounds. Eight is three tools
     * plus room to correct a mis-called one twice and still answer; the trace records however many
     * were actually made, so a run that hits the ceiling is visible rather than silent.
     */
    private int maxSteps = 8;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }
}
