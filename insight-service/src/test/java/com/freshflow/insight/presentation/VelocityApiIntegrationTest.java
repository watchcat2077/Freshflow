package com.freshflow.insight.presentation;

import com.freshflow.insight.infrastructure.InsightTestVelocityConfiguration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/insight/velocity/&#123;sku&#125;} (spec-04, operationId
 * {@code getVelocity}, FR12), generated from the contract BEFORE implementation (Continuous
 * Auto-Validation).
 *
 * <p><b>These tests are expected to FAIL when written.</b> No controller and no application
 * service exist yet, so every request resolves to no handler. A failure here is the specification
 * asserting itself against absent code.
 *
 * <p>The windowed aggregate is supplied by {@link InsightTestVelocityConfiguration} rather than by
 * a live Kafka Streams state store, and the clock is fixed. That is what makes these assertions
 * possible at all: a real windowed aggregate advances while the test runs, so "quantitySold is 5"
 * would be a race rather than a contract. The topology that fills the store for real is verified
 * end to end against the broker; see docs/ai-log/2026-09-07-week-7-fr12-velocity.md.
 *
 * <p>Fixed clock: {@code 2026-09-07T12:00:30Z}. Window: 60 seconds (spec-02 FR12's MVP
 * simplification).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: insight-service velocity API (FR12)")
class VelocityApiIntegrationTest {

    private static final String SKU = InsightTestVelocityConfiguration.SKU_ACTIVE;
    private static final String SKU_STALE = InsightTestVelocityConfiguration.SKU_STALE;
    private static final String SKU_NO_BASELINE = InsightTestVelocityConfiguration.SKU_NO_BASELINE;
    private static final String SKU_NEVER_SOLD = "SKU-NEVER-SOLD";

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------- GET /api/insight/velocity/{sku}

    /**
     * The whole contract in one request. Note what the numbers pin down:
     * <ul>
     *   <li>{@code quantitySold} 5 — the most recent <i>fully elapsed</i> window was chosen. The
     *       fixture's other windows carry 1 (started but not finished) and 99 (elapsed but older),
     *       so picking the newest-started, the largest, or the first would each fail here.</li>
     *   <li>{@code velocityPerHour} 300 — 5 units in a 60-second window is 300 units per hour.</li>
     *   <li>{@code velocityRatio} 2.5 — 300 against a stored baseline of 120, which is the
     *       comparison FR12 asks for.</li>
     * </ul>
     */
    @Test
    @DisplayName("GET /api/insight/velocity/{sku} -> 200 with the velocity view (FR12 happy path)")
    void getVelocity_whenWindowedDataExists_returns200AndVelocityResponse() throws Exception {
        mockMvc.perform(get("/api/insight/velocity/{sku}", SKU))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 VelocityResponse
                .andExpect(jsonPath("$.sku").value(SKU))
                .andExpect(jsonPath("$.windowStart").value("2026-09-07T11:59:25Z"))
                .andExpect(jsonPath("$.windowEnd").value("2026-09-07T12:00:25Z"))
                .andExpect(jsonPath("$.quantitySold").value(5))
                .andExpect(jsonPath("$.velocityPerHour").value(300.0))
                .andExpect(jsonPath("$.baselineVelocity").value(120.0))
                .andExpect(jsonPath("$.velocityRatio").value(2.5));
    }

    /**
     * Guarded against a vacuous pass, exactly as the batch and price 404 tests are: an unmapped
     * path also answers 404, so the status alone would go green against a service with no
     * controller at all.
     */
    @Test
    @DisplayName("GET /api/insight/velocity/{sku} -> 404 when no windowed data exists yet, from a real handler")
    void getVelocity_whenNoWindowedData_returns404FromAControllerNotFromAMissingMapping() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/insight/velocity/{sku}", SKU_NEVER_SOLD))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/insight/velocity/{sku} must be handled by a controller method. A null "
                        + "or non-HandlerMethod handler means the endpoint is simply not mapped, "
                        + "and the 404 above is Spring's no-such-route answer rather than spec-04's "
                        + "no-windowed-data answer.")
                .isInstanceOf(HandlerMethod.class);
    }

    /**
     * spec-04's 404 is "No windowed data for this SKU <b>yet</b>", and the same answer is owed once
     * the data has aged out. This SKU sold seven units, but the window closed more than a window
     * length before now. Reporting that as the current velocity would tell a manager the SKU is
     * still selling when it has not sold anything for five minutes — a stale read presented as a
     * live one, which is the specific failure this endpoint exists to prevent.
     */
    @Test
    @DisplayName("GET /api/insight/velocity/{sku} -> 404 when the only window is stale, not a stale velocity")
    void getVelocity_whenNewestWindowIsStale_returns404RatherThanAStaleNumber() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/insight/velocity/{sku}", SKU_STALE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.velocityPerHour").doesNotExist())
                .andReturn();

        // Same guard as above, and it is load-bearing here: without it this test would go green
        // against a service with no endpoint at all, since an unmapped path answers 404 too.
        assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
    }

    /**
     * FR12 compares against "a stored velocity baseline". Where none is stored there is nothing to
     * compare with, and the velocity itself is still perfectly reportable. spec-04 marks no
     * property of {@code VelocityResponse} required, so the two comparison fields are omitted.
     *
     * <p>The assertion that matters is the second pair: a baseline of 0.0 with a ratio of 0.0
     * would be a fabricated comparison, and would read as "this SKU is selling at zero times its
     * normal rate" when in fact its normal rate is unknown.
     */
    @Test
    @DisplayName("GET /api/insight/velocity/{sku} -> 200 without baseline fields when no baseline is stored")
    void getVelocity_whenNoBaselineStored_returns200AndOmitsTheComparison() throws Exception {
        mockMvc.perform(get("/api/insight/velocity/{sku}", SKU_NO_BASELINE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(SKU_NO_BASELINE))
                .andExpect(jsonPath("$.quantitySold").value(2))
                .andExpect(jsonPath("$.velocityPerHour").value(120.0))
                .andExpect(jsonPath("$.baselineVelocity").doesNotExist())
                .andExpect(jsonPath("$.velocityRatio").doesNotExist());
    }
}
