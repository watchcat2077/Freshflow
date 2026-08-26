package com.freshflow.pricing.presentation;

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
 * Integration tests for the {@code pricing-service} endpoint of spec-04, generated from the
 * contract BEFORE implementation (Continuous Auto-Validation, Phase 3).
 *
 * <p>Covers {@code GET /api/prices/&#123;sku&#125;} (operationId {@code getPrice}, FR8). Per FR8's
 * MVP scope note, "a read endpoint plus seeded configuration data is sufficient" — the price rules
 * these tests read are seeded from {@code src/test/resources/application-test.yml}.
 *
 * <p><b>These tests are expected to FAIL until Phase 4.</b>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: pricing-service price API")
class PriceApiIntegrationTest {

    /** Seeded in application-test.yml. */
    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    private static final String UNKNOWN_SKU = "SKU-NO-PRICE-RULE";

    @Autowired
    private MockMvc mockMvc;

    // --------------------------------------------------------- GET /api/prices/{sku}

    @Test
    @DisplayName("GET /api/prices/{sku} -> 200 with price rule and margin floor (FR8)")
    void getPrice_whenPriceRuleExists_returns200AndPriceResponse() throws Exception {
        mockMvc.perform(get("/api/prices/{sku}", SEEDED_SKU))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 PriceResponse
                .andExpect(jsonPath("$.sku").value(SEEDED_SKU))
                .andExpect(jsonPath("$.category").value("DAIRY"))
                .andExpect(jsonPath("$.basePrice.amount").value(4.50))
                .andExpect(jsonPath("$.basePrice.currency").value("AUD"))
                .andExpect(jsonPath("$.unitCost.amount").value(2.10))
                .andExpect(jsonPath("$.unitCost.currency").value("AUD"))
                // spec-03 s2: with no markdown in force, effectivePrice equals basePrice.
                // This is the only case that exists in Week 5.
                .andExpect(jsonPath("$.effectivePrice.amount").value(4.50))
                // spec-03 s3: PriceRule.marginFloor, exposed as marginFloorRatio
                .andExpect(jsonPath("$.marginFloorRatio").value(0.15))
                // spec-04 marks activeDiscountTier nullable; no markdown runs in Week 5.
                // FR9/FR11 are [MVP-PLUS] and POST /api/markdown-plans is out of scope.
                .andExpect(jsonPath("$.activeDiscountTier").doesNotExist());
    }

    /**
     * Guarded against a vacuous pass, for the same reason as the batch 404 test: an unmapped path
     * also answers 404. The handler assertion requires a real controller method to have produced
     * this 404, rather than Spring reporting that no such route exists.
     */
    @Test
    @DisplayName("GET /api/prices/{sku} -> 404 when no price rule exists, from a real handler")
    void getPrice_whenNoPriceRuleExists_returns404FromAControllerNotFromAMissingMapping() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/prices/{sku}", UNKNOWN_SKU))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/prices/{sku} must be handled by a controller method. A null or "
                        + "non-HandlerMethod handler means the endpoint is simply not mapped, and "
                        + "the 404 above is Spring's no-such-route answer rather than the "
                        + "contract's no-price-rule answer.")
                .isInstanceOf(HandlerMethod.class);
    }
}
