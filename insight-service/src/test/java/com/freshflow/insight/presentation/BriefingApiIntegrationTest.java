package com.freshflow.insight.presentation;

import com.freshflow.insight.application.StubBriefingAgent;
import com.freshflow.insight.infrastructure.InsightTestUnavailableBriefingConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/insight/briefings} and
 * {@code GET /api/insight/briefings/&#123;briefingId&#125;} (spec-04, operationIds
 * {@code createBriefing} and {@code getBriefing}, FR15), generated from the contract BEFORE
 * implementation (Continuous Auto-Validation). <b>Expected to FAIL when written</b>: no controller
 * exists yet, so every request resolves to no handler.
 *
 * <p>The agent is {@code StubBriefingAgent}, wired by {@code InsightTestBriefingConfiguration}; the
 * risk views, the velocity windows, the seeded FR14 trace and the fixed clock are the fixtures the
 * other endpoint tests already use. So the numbers below are the fixtures' numbers: two batches
 * flagged in {@code InsightTestVelocityConfiguration}, one trace completed on 10 September 2026 in
 * {@code InsightTestAgentTraceConfiguration}, and a clock fixed at 7 September 2026 12:00:30Z.
 *
 * <p>The stub's summary claims ninety-nine batches and forty markdowns. The response's counts are
 * two and one, or zero. That gap is spec-02 FR15's "no number in the response depends on the
 * model", asserted at the HTTP boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: insight-service briefings API (FR15)")
class BriefingApiIntegrationTest {

    private static final String BRIEFING_ID = "BRF-[0-9A-F]{8}";
    private static final String UNKNOWN_BRIEFING_ID = "BRF-DOESNOTEXIST";

    @Autowired
    private MockMvc mockMvc;

    // --------------------------------------------------------- POST /api/insight/briefings

    /**
     * The whole of spec-04's {@code BriefingResponse}, for a named business date on which the
     * seeded FR14 trace was completed.
     */
    @Test
    @DisplayName("POST /api/insight/briefings with a date -> 201 with the briefing, counts from the fixtures")
    void createBriefing_withBusinessDate_returns201AndBriefingResponse() throws Exception {
        mockMvc.perform(post("/api/insight/briefings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"2026-09-10\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("Location"))
                // spec-04 BriefingResponse
                .andExpect(jsonPath("$.briefingId", matchesPattern(BRIEFING_ID)))
                .andExpect(jsonPath("$.businessDate").value("2026-09-10"))
                .andExpect(jsonPath("$.generatedAt").value("2026-09-07T12:00:30Z"))
                .andExpect(jsonPath("$.summary").value(StubBriefingAgent.SUMMARY))
                .andExpect(jsonPath("$.riskCount").value(2))
                .andExpect(jsonPath("$.recommendationCount").value(1))
                // spec-04 BriefingResponse.toolCalls, in ascending sequence
                .andExpect(jsonPath("$.toolCalls", hasSize(3)))
                .andExpect(jsonPath("$.toolCalls[0].sequence").value(1))
                .andExpect(jsonPath("$.toolCalls[1].sequence").value(2))
                .andExpect(jsonPath("$.toolCalls[2].sequence").value(3))
                .andExpect(jsonPath("$.toolCalls[0].toolName").value("listCurrentRisks"))
                .andExpect(jsonPath("$.toolCalls[1].toolName").value("listRecommendations"))
                .andExpect(jsonPath("$.toolCalls[2].toolName").value("getSellThroughRate"))
                .andExpect(jsonPath("$.toolCalls[1].arguments").value("2026-09-10"))
                .andExpect(jsonPath("$.toolCalls[0].result").exists())
                .andExpect(jsonPath("$.toolCalls[0].timestamp").value("2026-09-07T12:00:30Z"));
    }

    /**
     * spec-04 {@code BriefingRequest}: "Defaults to today in UTC when the body is omitted". Today,
     * by the fixed clock, is 7 September 2026, on which no trace was completed.
     */
    @Test
    @DisplayName("POST /api/insight/briefings without a body -> 201 briefing today in UTC")
    void createBriefing_withoutBody_returns201ForTodayInUtc() throws Exception {
        mockMvc.perform(post("/api/insight/briefings"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessDate").value("2026-09-07"))
                .andExpect(jsonPath("$.riskCount").value(2))
                .andExpect(jsonPath("$.recommendationCount").value(0))
                .andExpect(jsonPath("$.toolCalls[1].arguments").value("2026-09-07"));
    }

    // ------------------------------------------------ GET /api/insight/briefings/{briefingId}

    /** FR15: "persisted and served by GET". The briefing just generated is the one served. */
    @Test
    @DisplayName("GET /api/insight/briefings/{id} -> 200 with the briefing that POST generated")
    void getBriefing_afterCreate_returns200AndTheSameBriefing() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/insight/briefings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"2026-09-10\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String briefingId = JsonPath.read(created.getResponse().getContentAsString(), "$.briefingId");

        mockMvc.perform(get("/api/insight/briefings/{briefingId}", briefingId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefingId").value(briefingId))
                .andExpect(jsonPath("$.businessDate").value("2026-09-10"))
                .andExpect(jsonPath("$.summary").value(StubBriefingAgent.SUMMARY))
                .andExpect(jsonPath("$.riskCount").value(2))
                .andExpect(jsonPath("$.recommendationCount").value(1))
                .andExpect(jsonPath("$.toolCalls", hasSize(3)))
                .andExpect(jsonPath("$.toolCalls[2].toolName").value("getSellThroughRate"));
    }

    /**
     * Guarded against a vacuous pass, exactly as the other 404 tests in this service are: an
     * unmapped path also answers 404, so the status alone would go green with no controller at all.
     */
    @Test
    @DisplayName("GET /api/insight/briefings/{id} -> 404 for an unknown id, from a real handler")
    void getBriefing_whenUnknownId_returns404FromAControllerNotFromAMissingMapping()
            throws Exception {
        MvcResult result = mockMvc
                .perform(get("/api/insight/briefings/{briefingId}", UNKNOWN_BRIEFING_ID))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/insight/briefings/{briefingId} must be handled by a controller "
                        + "method. A null or non-HandlerMethod handler means the endpoint is simply "
                        + "not mapped, and the 404 above is Spring's no-such-route answer rather "
                        + "than spec-04's briefing-not-found answer.")
                .isInstanceOf(HandlerMethod.class);
    }

    // ------------------------------------------------------------------ no agent configured

    /**
     * spec-02 FR15: "With no LLM configured the endpoint answers 503 and the service otherwise runs
     * normally". A second context with the test-profile stub switched off, so that no
     * {@code BriefingOrchestrator} bean exists &mdash; which is exactly the state of a production
     * context with no {@code LLM_API_KEY}.
     */
    @Nested
    @SpringBootTest(properties = "freshflow.insight.briefing.stub-agent=false")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("with no briefing agent configured")
    class WithoutAnAgent {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("POST /api/insight/briefings -> 503 AGENT_NOT_CONFIGURED")
        void createBriefing_withoutAnOrchestrator_returns503() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/insight/briefings"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // spec-04 ErrorResponse
                    .andExpect(jsonPath("$.error").value("AGENT_NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
        }

        /** "Otherwise runs normally": the read side of FR15 does not need a model. */
        @Test
        @DisplayName("GET /api/insight/briefings/{id} still answers 404 from a real handler")
        void getBriefing_withoutAnOrchestrator_stillAnswers() throws Exception {
            MvcResult result = mockMvc
                    .perform(get("/api/insight/briefings/{briefingId}", UNKNOWN_BRIEFING_ID))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
        }
    }

    // ------------------------------------------------------------------ model unusable

    /**
     * The third state, found in the live run of 5 October 2026: an agent is configured and its
     * model cannot be used (that afternoon, a 429 daily-quota answer from Gemini), which until
     * then surfaced as a 500 with a stack trace. spec-04 gives this endpoint two answers, 201 and
     * 503; an unusable model is the second, with its own {@code error} code so that a client can
     * tell "not configured" from "configured, but down right now", and nothing is stored.
     */
    @Nested
    @SpringBootTest(properties = "freshflow.insight.briefing.stub-agent=unavailable")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("with a briefing agent whose model cannot be used")
    class WithAnUnavailableModel {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("POST /api/insight/briefings -> 503 AGENT_UNAVAILABLE naming the reason")
        void createBriefing_whenTheModelIsUnavailable_returns503() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/insight/briefings"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("AGENT_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                            InsightTestUnavailableBriefingConfiguration.REASON_FRAGMENT)))
                    .andReturn();

            assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
        }

        /** Nothing was generated, so nothing is readable: the store is untouched by the failure. */
        @Test
        @DisplayName("GET /api/insight/briefings/{id} still answers 404 from a real handler")
        void getBriefing_whenTheModelIsUnavailable_stillAnswers() throws Exception {
            MvcResult result = mockMvc
                    .perform(get("/api/insight/briefings/{briefingId}", UNKNOWN_BRIEFING_ID))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
        }
    }
}
