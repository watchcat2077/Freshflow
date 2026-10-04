package com.freshflow.inventory.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.contracts.MoneyPayload;
import com.freshflow.inventory.infrastructure.ItemSoldMessageHandler;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code inventory-service} endpoints of spec-04, generated from the
 * contract BEFORE implementation (Continuous Auto-Validation, Phase 3).
 *
 * <p>Covers {@code POST /api/batches} (operationId {@code createBatch}, FR1),
 * {@code GET /api/batches/&#123;batchId&#125;} (operationId {@code getBatch}, FR4) and, since
 * 4 October 2026, {@code GET /api/batches?sku=} (operationId {@code listBatchesBySku}, FR4).
 *
 * <p><b>These tests are expected to FAIL until Phase 4.</b> No controller exists yet, so every
 * request currently resolves to no handler. A failure here is the specification asserting itself
 * against absent code; it is not a defect in the test. The FR4 list tests were likewise written to
 * fail before the endpoint was mapped.
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

    @Autowired
    private ItemSoldMessageHandler itemSoldMessageHandler;

    /**
     * spec-04 {@code CreateBatchRequest}. {@code expiryAt} is computed relative to now rather than
     * copied from spec-04's {@code yoghurt} example, whose literal {@code 2026-08-18T22:00:00Z} is
     * already in the past and would therefore breach FR1's "expiry must be in the future" rule.
     */
    private String createBatchRequest(int quantity, Instant expiryAt) {
        return createBatchRequest(SKU, quantity, expiryAt);
    }

    private String createBatchRequest(String sku, int quantity, Instant expiryAt) {
        return """
                {
                  "sku": "%s",
                  "quantity": %d,
                  "expiryAt": "%s",
                  "unitCost": { "amount": 2.10, "currency": "AUD" },
                  "supplierRef": "DEL-88213"
                }""".formatted(sku, quantity, expiryAt.toString());
    }

    /**
     * Creates a batch through the API and returns its batchId. Seeded through the API itself:
     * spec-04 defines no other way to create a batch, and going around the API would test less
     * than the contract promises.
     */
    private String createBatch(String sku, int quantity, Instant expiryAt) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBatchRequest(sku, quantity, expiryAt)))
                .andExpect(status().isCreated())
                .andReturn();

        String batchId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("batchId").asText();
        assertThat(batchId).as("batchId returned by POST /api/batches").isNotBlank();
        return batchId;
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

    // ------------------------------------------------------ GET /api/batches?sku=

    /**
     * FR4: "{@code GET /api/batches?sku=} returns the ACTIVE batches of a SKU in
     * first-expired-first-out order, the same order FR2 deducts them in." The later-expiring batch
     * is created FIRST, so that an implementation returning batches in insertion order fails
     * rather than passing by accident.
     *
     * <p>Every FR4 list test uses its own SKU. The context, and therefore the H2 instance, is
     * shared across the class, and SKU-YOG-1KG already carries whatever the FR1 tests created.
     */
    @Test
    @DisplayName("GET /api/batches?sku= -> 200 with the SKU's active batches, soonest expiry first (FR4)")
    void listBatches_bySku_returnsActiveBatchesSoonestExpiryFirst() throws Exception {
        String sku = "SKU-FR4-FEFO";
        String later = createBatch(sku, 10, Instant.now().plus(48, ChronoUnit.HOURS));
        String soonest = createBatch(sku, 4, Instant.now().plus(6, ChronoUnit.HOURS));

        MvcResult result = mockMvc.perform(get("/api/batches").param("sku", sku))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                // FEFO: the batch expiring in six hours before the one expiring in two days
                .andExpect(jsonPath("$[0].batchId").value(soonest))
                .andExpect(jsonPath("$[1].batchId").value(later))
                // spec-04 BatchResponse, per entry
                .andExpect(jsonPath("$[0].sku").value(sku))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].remainingQuantity").value(4))
                .andExpect(jsonPath("$[0].remainingShelfLifeHours").isNumber())
                .andExpect(jsonPath("$[1].sku").value(sku))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].remainingQuantity").value(10))
                .andExpect(jsonPath("$[1].remainingShelfLifeHours").isNumber())
                .andReturn();

        JsonNode batches = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(batches.get(0).path("remainingShelfLifeHours").asLong())
                .as("remainingShelfLifeHours is computed at the moment of the request, so FEFO "
                        + "order is also ascending shelf-life order")
                .isLessThan(batches.get(1).path("remainingShelfLifeHours").asLong());
    }

    /**
     * FR4: "an empty array is a valid answer". spec-04 is explicit that a SKU with no active batch
     * is not an error, which distinguishes this endpoint from {@code GET /api/batches/{batchId}},
     * where an unknown identity is a 404.
     */
    @Test
    @DisplayName("GET /api/batches?sku= -> 200 with an empty array for a SKU with no active batch (FR4)")
    void listBatches_forUnknownSku_returns200AndEmptyArray() throws Exception {
        mockMvc.perform(get("/api/batches").param("sku", "SKU-FR4-NO-SUCH-SKU"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * FR4 lists ACTIVE batches. A batch that FR2 has emptied is DEPLETED (spec-03 §1, invariant 5)
     * and drops out of the list, which is what makes the answer "what is on the shelf" rather than
     * "what was ever delivered". The sale is applied through FR2's inbound adapter, exactly as
     * {@code ItemSoldConsumptionTest} does, with only the Kafka transport bypassed; FEFO draws the
     * whole sale from the soonest-expiring batch, which is the one that empties.
     */
    @Test
    @DisplayName("GET /api/batches?sku= omits a batch that FR2 has depleted (FR4: ACTIVE only)")
    void listBatches_bySku_omitsDepletedBatches() throws Exception {
        String sku = "SKU-FR4-DEPLETED";
        createBatch(sku, 3, Instant.now().plus(6, ChronoUnit.HOURS));
        String stillActive = createBatch(sku, 10, Instant.now().plus(48, ChronoUnit.HOURS));
        Instant soldAt = Instant.now();
        itemSoldMessageHandler.handle(new ItemSoldEvent(UUID.randomUUID(), soldAt, "TXN-FR4", sku, 3,
                new MoneyPayload(new BigDecimal("4.50"), "AUD"), soldAt));

        mockMvc.perform(get("/api/batches").param("sku", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].batchId").value(stillActive))
                .andExpect(jsonPath("$[0].remainingQuantity").value(10));
    }

    /**
     * Guarded against a vacuous pass in the same way as the 404 test above. Spring answers 400 by
     * itself for a missing required query parameter and never reaches a controller; spec-01 §1.1
     * (finding M-9) wants the validation hand-rolled in the application layer and returned as a
     * typed result that the controller maps to spec-04's {@code ErrorResponse}. So the request must
     * reach a {@link HandlerMethod}, and the body must be the contract's, not the framework's.
     */
    @Test
    @DisplayName("GET /api/batches without sku -> 400 ErrorResponse from a real handler, not the framework (FR4)")
    void listBatches_withoutSku_returns400FromAControllerNotFromTheFramework() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/batches"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 ErrorResponse
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/batches must be handled by a controller method. A null or "
                        + "non-HandlerMethod handler means the 400 above is Spring's "
                        + "missing-parameter answer rather than the application layer's typed "
                        + "validation result.")
                .isInstanceOf(HandlerMethod.class);

        // A present but blank sku is the same answer: spec-04 says "missing or blank".
        mockMvc.perform(get("/api/batches").param("sku", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
