package com.freshflow.sales.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code sales-service} endpoint of spec-04, generated from the contract
 * BEFORE implementation (Continuous Auto-Validation, Phase 3).
 *
 * <p>Covers {@code POST /api/transactions} (operationId {@code createTransaction}), which spec-04
 * ties to FR5 and FR6a.
 *
 * <p>The base price is read from {@code sales-service}'s own local {@code PriceSnapshot}, seeded
 * for these tests from static test configuration through the {@code PriceSnapshotFeed} inbound
 * port (spec-01 §1.3, spec-03 §2, ADR 0001). No call to {@code pricing-service} is made or
 * mocked, because none is permitted to exist. See {@code src/test/resources/application-test.yml}.
 *
 * <p><b>These tests are expected to FAIL until Phase 4.</b>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: sales-service transaction API")
class TransactionApiIntegrationTest {

    /** Seeded in application-test.yml. */
    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    /** {@code freshflow.sales.price-snapshot-seed[0].base-price} in application-test.yml. */
    private static final double SEEDED_BASE_PRICE = 4.50;

    private static final String UNKNOWN_SKU = "SKU-NOT-IN-SNAPSHOT";

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------ POST /api/transactions

    /**
     * The shape of spec-04's {@code singleScan} example, with {@code quantity} raised from 1 to 2
     * so that FR6a's "{@code totalAmount} is the sum of {@code unitPrice x quantity}" is actually
     * falsifiable. At quantity 1 that rule and "{@code unitPrice} equals the base price" make the
     * same claim, and a wrong implementation would satisfy both.
     */
    @Test
    @DisplayName("POST /api/transactions -> 201, base price applied, markdownApplied false (FR5, FR6a)")
    void postTransaction_withKnownSku_returns201AndAppliesSeededBasePrice() throws Exception {
        String body = """
                {
                  "tillId": "TILL-02",
                  "occurredAt": "%s",
                  "lineItems": [
                    { "sku": "%s", "quantity": 2 }
                  ]
                }""".formatted(Instant.now().toString(), SEEDED_SKU);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 TransactionResponse
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.tillId").value("TILL-02"))
                .andExpect(jsonPath("$.occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.lineItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.lineItems[0].sku").value(SEEDED_SKU))
                .andExpect(jsonPath("$.lineItems[0].quantity").value(2))
                // FR6a: unitPrice equals PriceSnapshot.basePrice for that SKU
                .andExpect(jsonPath("$.lineItems[0].unitPrice.amount").value(SEEDED_BASE_PRICE))
                .andExpect(jsonPath("$.lineItems[0].unitPrice.currency").value("AUD"))
                // FR6a: markdownApplied is false — FR6b is [MVP-PLUS] and out of scope here
                .andExpect(jsonPath("$.lineItems[0].markdownApplied").value(false))
                // FR6a: totalAmount is the sum of unitPrice x quantity
                .andExpect(jsonPath("$.totalAmount.amount").value(SEEDED_BASE_PRICE * 2))
                .andExpect(jsonPath("$.totalAmount.currency").value("AUD"));
    }

    @Test
    @DisplayName("POST /api/transactions -> 400 when lineItems is empty (spec-04 minItems: 1)")
    void postTransaction_withEmptyLineItems_returns400() throws Exception {
        String body = """
                {
                  "tillId": "TILL-02",
                  "occurredAt": "%s",
                  "lineItems": []
                }""".formatted(Instant.now().toString());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /**
     * FR6a, final acceptance criterion: "A scan of a SKU absent from the snapshot is a 400, never
     * a silent zero price." spec-04's 400 description for this endpoint lists only "empty line
     * items or non-positive quantity" and omits this case — reported as an inconsistency in the
     * Phase 3 log. The behaviour itself is unambiguous in spec-02, so the test asserts it.
     */
    @Test
    @DisplayName("POST /api/transactions -> 400 when the SKU is absent from the PriceSnapshot (FR6a)")
    void postTransaction_withSkuAbsentFromSnapshot_returns400AndNeverASilentZeroPrice() throws Exception {
        String body = """
                {
                  "tillId": "TILL-02",
                  "occurredAt": "%s",
                  "lineItems": [
                    { "sku": "%s", "quantity": 1 }
                  ]
                }""".formatted(Instant.now().toString(), UNKNOWN_SKU);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                // Guards the "never a silent zero price" half of the criterion: a 201 carrying a
                // zero-priced line item must not be able to satisfy this test.
                .andExpect(jsonPath("$.transactionId").doesNotExist());
    }
}
