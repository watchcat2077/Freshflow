package com.freshflow.insight.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/insight/risks} (spec-04, operationId {@code listRisks},
 * FR13), generated from the contract BEFORE implementation.
 *
 * <p>The risk views are supplied by {@code InsightTestVelocityConfiguration} rather than by a live
 * Kafka Streams state store, for the same reason as the velocity tests: a live topology moves
 * while the test runs, so exact assertions would be a race. The topology that fills the store for
 * real is verified end to end against the broker; see
 * docs/ai-log/2026-09-07-week-7-fr13-risk.md.
 *
 * <p><b>Expected to FAIL when written</b> — no controller exists yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: insight-service risks API (FR13)")
class RiskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * spec-04 says "batches <b>currently flagged</b> as at risk". The fixture holds three assessed
     * batches, only two of them flagged, so a handler that simply returned everything the store
     * held would fail here — which is the point of seeding a safe batch alongside the risky ones.
     */
    @Test
    @DisplayName("GET /api/insight/risks -> 200 listing only the batches currently at risk")
    void listRisks_returns200AndOnlyFlaggedBatches() throws Exception {
        mockMvc.perform(get("/api/insight/risks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                // Worst first: the larger projected shortfall is the batch to mark down first.
                .andExpect(jsonPath("$[0].batchId").value("BAT-WORST"))
                .andExpect(jsonPath("$[1].batchId").value("BAT-MILD"))
                // spec-04 RiskResponse
                .andExpect(jsonPath("$[0].sku").value("SKU-YOG-1KG"))
                .andExpect(jsonPath("$[0].remainingQuantity").value(40))
                .andExpect(jsonPath("$[0].hoursToExpiry").value(10))
                .andExpect(jsonPath("$[0].currentVelocity").value(2.0))
                .andExpect(jsonPath("$[0].projectedSales").value(20.0))
                .andExpect(jsonPath("$[0].projectedShortfall").value(20.0))
                .andExpect(jsonPath("$[0].detectedAt").value("2026-09-07T12:00:00Z"));
    }

    /**
     * The safe batch must not appear anywhere in the response, under any key. Asserting the size
     * alone would pass if the handler returned the right count of the wrong rows.
     */
    @Test
    @DisplayName("GET /api/insight/risks -> a batch that will clear in time is absent entirely")
    void listRisks_excludesBatchesThatWillClear() throws Exception {
        mockMvc.perform(get("/api/insight/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.batchId == 'BAT-SAFE')]", hasSize(0)));
    }
}
