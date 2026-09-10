package com.freshflow.insight.presentation;

import com.freshflow.insight.infrastructure.InsightTestAgentTraceConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/insight/agent-traces/&#123;agentTraceId&#125;} (spec-04,
 * operationId {@code getAgentTrace}, FR14), generated from the contract BEFORE implementation
 * (Continuous Auto-Validation).
 *
 * <p><b>Expected to FAIL when written.</b> {@code AgentTraceController} exists so that this module
 * compiles and so that the 404 test below can distinguish a real handler from a missing route, but
 * its body throws {@code UnsupportedOperationException("FR14 Phase B")}.
 *
 * <p>The trace is supplied by {@link InsightTestAgentTraceConfiguration} rather than by the database,
 * exactly as the velocity and risk tests take their read models from a fixture. The persistence
 * adapter has its own {@code @DataJpaTest} slice; what these two tests are about is the HTTP
 * contract.
 *
 * <p>This endpoint is what FR14's "full tool-call trace persisted and queryable" actually means to
 * a user, and it is the mechanism by which an automated markdown stays reviewable: the trace id
 * travels on {@code markdown-recommended}, so a proposal that Pricing accepted and one it rejected
 * can each be traced back to the evidence behind it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: insight-service agent traces API (FR14)")
class AgentTraceApiIntegrationTest {

    private static final String TRACE_ID = InsightTestAgentTraceConfiguration.SEEDED_TRACE_ID;
    private static final String UNKNOWN_TRACE_ID = "TRC-DOES-NOT-EXIST";

    @Autowired
    private MockMvc mockMvc;

    /**
     * The whole of spec-04's {@code AgentTraceResponse}, including the nested tool calls.
     *
     * <p>The assertion that carries the weight is the {@code sequence} array coming back 1, 2, 3
     * alongside the tool names in matching order. spec-04 gives each call a sequence, so the
     * ordering is part of the contract rather than a convenience; a response that listed the right
     * three calls in a different order would still satisfy a size assertion and would misrepresent
     * what the agent did.
     */
    @Test
    @DisplayName("GET /api/insight/agent-traces/{id} -> 200 with the full trace, calls in order")
    void getAgentTrace_whenTraceExists_returns200AndAgentTraceResponse() throws Exception {
        mockMvc.perform(get("/api/insight/agent-traces/{agentTraceId}", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 AgentTraceResponse
                .andExpect(jsonPath("$.agentTraceId").value(TRACE_ID))
                .andExpect(jsonPath("$.triggerEventId")
                        .value(InsightTestAgentTraceConfiguration.SEEDED_TRIGGER_EVENT_ID))
                .andExpect(jsonPath("$.batchId")
                        .value(InsightTestAgentTraceConfiguration.SEEDED_BATCH_ID))
                .andExpect(jsonPath("$.finalRecommendation")
                        .value(InsightTestAgentTraceConfiguration.FINAL_RECOMMENDATION))
                .andExpect(jsonPath("$.rationale")
                        .value(InsightTestAgentTraceConfiguration.RATIONALE))
                .andExpect(jsonPath("$.completedAt").value("2026-09-10T09:30:00Z"))
                // spec-04 AgentTraceResponse.toolCalls, in ascending sequence
                .andExpect(jsonPath("$.toolCalls", hasSize(3)))
                .andExpect(jsonPath("$.toolCalls[0].sequence").value(1))
                .andExpect(jsonPath("$.toolCalls[1].sequence").value(2))
                .andExpect(jsonPath("$.toolCalls[2].sequence").value(3))
                .andExpect(jsonPath("$.toolCalls[0].toolName").value("getBatchDetails"))
                .andExpect(jsonPath("$.toolCalls[1].toolName").value("getSellThroughRate"))
                .andExpect(jsonPath("$.toolCalls[2].toolName").value("proposeMarkdown"))
                .andExpect(jsonPath("$.toolCalls[0].arguments").value("BAT-0001"))
                .andExpect(jsonPath("$.toolCalls[0].result").exists())
                .andExpect(jsonPath("$.toolCalls[0].timestamp").value("2026-09-10T09:29:58Z"));
    }

    /**
     * Guarded against a vacuous pass, exactly as {@code VelocityApiIntegrationTest} is: an unmapped
     * path also answers 404, so the status alone would go green against a service with no
     * controller at all.
     */
    @Test
    @DisplayName("GET /api/insight/agent-traces/{id} -> 404 for an unknown id, from a real handler")
    void getAgentTrace_whenUnknownId_returns404FromAControllerNotFromAMissingMapping()
            throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/insight/agent-traces/{agentTraceId}", UNKNOWN_TRACE_ID))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/insight/agent-traces/{agentTraceId} must be handled by a controller "
                        + "method. A null or non-HandlerMethod handler means the endpoint is simply "
                        + "not mapped, and the 404 above is Spring's no-such-route answer rather "
                        + "than spec-04's trace-not-found answer.")
                .isInstanceOf(HandlerMethod.class);
    }
}
