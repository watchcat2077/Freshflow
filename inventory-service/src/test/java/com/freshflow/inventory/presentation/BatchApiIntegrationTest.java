package com.freshflow.inventory.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code inventory-service} endpoints of spec-04, generated from the
 * contract BEFORE implementation (Continuous Auto-Validation, Phase 3).
 *
 * <p>Covers {@code POST /api/batches} (operationId {@code createBatch}, FR1) and
 * {@code GET /api/batches/&#123;batchId&#125;} (operationId {@code getBatch}, FR4).
 *
 * <p><b>These tests are expected to FAIL until Phase 4.</b> No controller exists yet, so every
 * request currently resolves to no handler. A failure here is the specification asserting itself
 * against absent code; it is not a defect in the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: inventory-service batch API")
class BatchApiIntegrationTest {

    private static final String SKU = "SKU-YOG-1KG";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * spec-04 {@code CreateBatchRequest}. {@code expiryAt} is computed relative to now rather than
     * copied from spec-04's {@code yoghurt} example, whose literal {@code 2026-08-18T22:00:00Z} is
     * already in the past and would therefore breach FR1's "expiry must be in the future" rule.
     */
    private String createBatchRequest(int quantity, Instant expiryAt) {
        return """
                {
                  "sku": "%s",
                  "quantity": %d,
                  "expiryAt": "%s",
                  "unitCost": { "amount": 2.10, "currency": "AUD" },
                  "supplierRef": "DEL-88213"
                }""".formatted(SKU, quantity, expiryAt.toString());
    }

    // ---------------------------------------------------------------- POST /api/batches

    @Test
    @DisplayName("POST /api/batches -> 201 with the created batch (FR1 happy path)")
    void postBatch_withValidPayload_returns201AndBatchResponse() throws Exception {
        Instant expiryAt = Instant.now().plus(48, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBatchRequest(50, expiryAt)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 BatchResponse
                .andExpect(jsonPath("$.batchId").isNotEmpty())
                .andExpect(jsonPath("$.sku").value(SKU))
                .andExpect(jsonPath("$.receivedQuantity").value(50))
                // FR1 / spec-03 s1: a newly received batch has nothing deducted yet
                .andExpect(jsonPath("$.remainingQuantity").value(50))
                .andExpect(jsonPath("$.unitCost.amount").value(2.10))
                .andExpect(jsonPath("$.unitCost.currency").value("AUD"))
                // spec-03 s1: BatchStatus on creation
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.remainingShelfLifeHours").isNumber());
    }

    @Test
    @DisplayName("POST /api/batches -> 400 when expiryAt is in the past (FR1: expiry must be future)")
    void postBatch_withPastExpiry_returns400() throws Exception {
        Instant expiryAt = Instant.now().minus(24, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBatchRequest(50, expiryAt)))
                .andExpect(status().isBadRequest())
                // spec-04 ErrorResponse
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/batches -> 400 when quantity is not positive (FR1, CreateBatchRequest minimum 1)")
    void postBatch_withNonPositiveQuantity_returns400() throws Exception {
        Instant expiryAt = Instant.now().plus(48, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBatchRequest(0, expiryAt)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ------------------------------------------------- GET /api/batches/{batchId}

    @Test
    @DisplayName("GET /api/batches/{batchId} -> 200 for a batch that exists (FR4)")
    void getBatch_whenBatchExists_returns200AndBatchResponse() throws Exception {
        Instant expiryAt = Instant.now().plus(48, ChronoUnit.HOURS);

        // Seeded through the API itself: spec-04 defines no other way to create a batch,
        // and going around the API would test less than the contract promises.
        MvcResult created = mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBatchRequest(50, expiryAt)))
                .andExpect(status().isCreated())
                .andReturn();

        String batchId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("batchId").asText();
        assertThat(batchId).as("batchId returned by POST /api/batches").isNotBlank();

        mockMvc.perform(get("/api/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.sku").value(SKU))
                .andExpect(jsonPath("$.receivedQuantity").value(50))
                .andExpect(jsonPath("$.remainingQuantity").value(50))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * Guarded against a vacuous pass. An unmapped path also answers 404, so asserting the status
     * alone would let this test go green against a service with no controller at all. The extra
     * assertion requires the request to have been dispatched to a {@link HandlerMethod} — that is,
     * to a real controller method that deliberately chose to report "not found".
     */
    @Test
    @DisplayName("GET /api/batches/{batchId} -> 404 for an unknown batch, from a real handler")
    void getBatch_whenBatchDoesNotExist_returns404FromAControllerNotFromAMissingMapping() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/batches/{batchId}", "BAT-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/batches/{batchId} must be handled by a controller method. "
                        + "A null or non-HandlerMethod handler means the endpoint is simply not "
                        + "mapped, and the 404 above is Spring's no-such-route answer rather than "
                        + "the contract's batch-not-found answer.")
                .isInstanceOf(HandlerMethod.class);
    }
}
