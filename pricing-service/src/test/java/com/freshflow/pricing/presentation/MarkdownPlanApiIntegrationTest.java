package com.freshflow.pricing.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshflow.pricing.infrastructure.PricingTestMessagingConfiguration;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code pricing-service} markdown endpoints of spec-04, generated from
 * the contract BEFORE implementation (Continuous Auto-Validation, Phase 3 discipline).
 *
 * <p>Covers {@code POST /api/markdown-plans} (operationId {@code createMarkdownPlan}, FR9 and
 * FR11) and {@code GET /api/markdown-plans/&#123;markdownPlanId&#125;} (operationId
 * {@code getMarkdownPlan}, FR11's "rejection is recorded and queryable"). The numbers are the ones
 * team ruling B-3 wrote into spec-03 §3 against the seeded SKU-YOG-1KG: PERCENT_30 gives 3.15 and
 * passes; PERCENT_60 gives 1.80 and -0.1667 and is refused.
 *
 * <p>Promotion windows are computed relative to now rather than copied from spec-04's examples,
 * per the contract's own timestamp rule. Windows for the seeded SKU are placed in the <i>future</i>
 * on purpose: the Spring context, and with it the H2 instance, is shared with
 * {@code PriceApiIntegrationTest}, which asserts that the seeded SKU is at its base price. The one
 * test that needs a markdown in force right now creates its own SKU through
 * {@code PUT /api/prices/&#123;sku&#125;} with the same economics.
 *
 * <p><b>These tests are expected to FAIL until the endpoints exist.</b> A failure here is the
 * specification asserting itself against absent code; it is not a defect in the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: pricing-service markdown plan API")
class MarkdownPlanApiIntegrationTest {

    /** Seeded in application-test.yml: base 4.50, cost 2.10, floor 0.15. */
    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    private static final String UNKNOWN_SKU = "SKU-NO-PRICE-RULE";

    private static final String BREACH_MESSAGE =
            "Discount tier PERCENT_60 yields margin -0.1667, below floor 0.15";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingTestMessagingConfiguration.RecordingMarkdownEventPublisher markdownEvents;

    @BeforeEach
    void clearRecordedEvents() {
        markdownEvents.clear();
    }

    /** Truncated to seconds so that an echoed timestamp survives the round trip through H2 intact. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** spec-04 {@code CreateMarkdownPlanRequest}. */
    private static String createPlanRequest(String batchId, String sku, String tier, Instant from,
                                            Instant to, String origin, String rationale,
                                            String agentTraceId) {
        return """
                {
                  "batchId": "%s",
                  "sku": "%s",
                  "discountTier": "%s",
                  "effectiveFrom": "%s",
                  "effectiveTo": "%s",
                  "origin": "%s",
                  "rationale": "%s",
                  "agentTraceId": %s
                }""".formatted(batchId, sku, tier, from, to, origin, rationale,
                agentTraceId == null ? "null" : "\"" + agentTraceId + "\"");
    }

    private String postPlan(String body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/markdown-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    // ------------------------------------------------------ POST -> 201 (FR9)

    @Test
    @DisplayName("POST /api/markdown-plans -> 201 ACTIVE at 3.15 for PERCENT_30, and publishes markdown-applied (FR9)")
    void postPlan_withinTheFloor_returns201ActiveAndPublishes() throws Exception {
        Instant from = now().plus(1, ChronoUnit.DAYS);
        Instant to = from.plus(36, ChronoUnit.HOURS);

        String body = postPlan(createPlanRequest("BAT-0001", SEEDED_SKU, "PERCENT_30", from, to,
                "HUMAN", "41 units remaining, 9 trading hours to expiry, velocity 2.1/h", null), 201);

        // spec-04 MarkdownPlanResponse
        String planId = objectMapper.readTree(body).path("markdownPlanId").asText();
        assertThat(planId).isNotBlank();
        mockMvc.perform(get("/api/markdown-plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.markdownPlanId").value(planId))
                .andExpect(jsonPath("$.batchId").value("BAT-0001"))
                .andExpect(jsonPath("$.sku").value(SEEDED_SKU))
                .andExpect(jsonPath("$.discountTier").value("PERCENT_30"))
                .andExpect(jsonPath("$.effectiveFrom").value(from.toString()))
                .andExpect(jsonPath("$.effectiveTo").value(to.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.origin").value("HUMAN"))
                // spec-03 s3 (B-3): 4.50 x 0.70 = 3.15
                .andExpect(jsonPath("$.discountedPrice.amount").value(3.15))
                .andExpect(jsonPath("$.discountedPrice.currency").value("AUD"))
                .andExpect(jsonPath("$.rationale").value("41 units remaining, 9 trading hours to expiry, velocity 2.1/h"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // The 201 body itself is the same representation.
        assertThat(objectMapper.readTree(body).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(objectMapper.readTree(body).path("discountedPrice").path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("3.15"));

        // FR9: "Publishes markdown-applied on success" -- exactly one, for this plan, at its price.
        assertThat(markdownEvents.published()).hasSize(1);
        assertThat(markdownEvents.published().get(0).markdownPlanId().value()).isEqualTo(planId);
        assertThat(markdownEvents.published().get(0).sku().value()).isEqualTo(SEEDED_SKU);
        assertThat(markdownEvents.published().get(0).discountedPrice().amount())
                .isEqualByComparingTo(new BigDecimal("3.15"));

        // The window is tomorrow, so nothing is in force yet: the seeded price is untouched.
        mockMvc.perform(get("/api/prices/{sku}", SEEDED_SKU))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePrice.amount").value(4.50))
                .andExpect(jsonPath("$.activeDiscountTier").doesNotExist());
    }

    // ------------------------------------------------------ POST -> 422 (FR11)

    @Test
    @DisplayName("POST /api/markdown-plans -> 422 MarginFloorRejection for PERCENT_60, recorded as a REJECTED plan (FR11)")
    void postPlan_breachingTheFloor_returns422AndRecordsTheRejection() throws Exception {
        Instant from = now().plus(1, ChronoUnit.DAYS);
        Instant to = from.plus(36, ChronoUnit.HOURS);

        MvcResult rejected = mockMvc.perform(post("/api/markdown-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanRequest("BAT-0001", SEEDED_SKU, "PERCENT_60", from, to,
                                "AGENT", "Aggressive clearance", "TRC-0002")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 MarginFloorRejection, with the numbers of spec-03 s3 (B-3)
                .andExpect(jsonPath("$.error").value("MARGIN_FLOOR_BREACH"))
                .andExpect(jsonPath("$.sku").value(SEEDED_SKU))
                .andExpect(jsonPath("$.requestedTier").value("PERCENT_60"))
                .andExpect(jsonPath("$.resultingPrice.amount").value(1.80))
                .andExpect(jsonPath("$.resultingPrice.currency").value("AUD"))
                .andExpect(jsonPath("$.unitCost.amount").value(2.10))
                .andExpect(jsonPath("$.unitCost.currency").value("AUD"))
                .andExpect(jsonPath("$.resultingMarginRatio").value(-0.1667))
                .andExpect(jsonPath("$.marginFloorRatio").value(0.15))
                .andExpect(jsonPath("$.message").value(BREACH_MESSAGE))
                // "queryable by its id" needs the id; see the ai-log for why it is carried here.
                .andExpect(jsonPath("$.markdownPlanId").isNotEmpty())
                .andReturn();

        String planId = objectMapper.readTree(rejected.getResponse().getContentAsString())
                .path("markdownPlanId").asText();

        // FR11: "Rejection is recorded and queryable."
        mockMvc.perform(get("/api/markdown-plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdownPlanId").value(planId))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.discountTier").value("PERCENT_60"))
                .andExpect(jsonPath("$.origin").value("AGENT"))
                .andExpect(jsonPath("$.agentTraceId").value("TRC-0002"))
                .andExpect(jsonPath("$.rationale").value("Aggressive clearance"))
                .andExpect(jsonPath("$.rejectionReason").value(BREACH_MESSAGE))
                // spec-04: "Present when the plan is ACTIVE; absent for a REJECTED plan"
                .andExpect(jsonPath("$.discountedPrice").doesNotExist());

        // Nothing reached the till.
        assertThat(markdownEvents.published()).isEmpty();
    }

    // ---------------------------------------------------------- POST -> 400

    @Test
    @DisplayName("POST /api/markdown-plans -> 400 when a required field is missing")
    void postPlan_withMissingField_returns400() throws Exception {
        Instant from = now().plus(1, ChronoUnit.DAYS);
        String missingTier = """
                {
                  "batchId": "BAT-0001",
                  "sku": "%s",
                  "effectiveFrom": "%s",
                  "effectiveTo": "%s",
                  "origin": "HUMAN"
                }""".formatted(SEEDED_SKU, from, from.plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/markdown-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingTier))
                .andExpect(status().isBadRequest())
                // spec-04 ErrorResponse
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(markdownEvents.published()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/markdown-plans -> 400 for a tier outside the spec-04 enumeration")
    void postPlan_withUnknownTier_returns400() throws Exception {
        Instant from = now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/markdown-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanRequest("BAT-0001", SEEDED_SKU, "PERCENT_45", from,
                                from.plus(1, ChronoUnit.DAYS), "HUMAN", "Half-ish price", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNKNOWN_TIER"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(markdownEvents.published()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/markdown-plans -> 400 when effectiveTo is not after effectiveFrom (spec-03 s3 invariant 2)")
    void postPlan_withInvertedWindow_returns400() throws Exception {
        Instant from = now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/markdown-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanRequest("BAT-0001", SEEDED_SKU, "PERCENT_30", from,
                                from.minus(1, ChronoUnit.HOURS), "HUMAN", "Backwards", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_WINDOW"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(markdownEvents.published()).isEmpty();
    }

    // ---------------------------------------------------------- POST -> 404

    /**
     * Guarded against a vacuous pass, like every 404 in this suite: an unmapped path also answers
     * 404, so the request must have reached a real controller method that chose to say "no price
     * rule, so no floor to check against".
     */
    @Test
    @DisplayName("POST /api/markdown-plans -> 404 from a real handler when the SKU has no price rule")
    void postPlan_forSkuWithoutRule_returns404FromAControllerNotFromAMissingMapping() throws Exception {
        Instant from = now().plus(1, ChronoUnit.DAYS);

        MvcResult result = mockMvc.perform(post("/api/markdown-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanRequest("BAT-0009", UNKNOWN_SKU, "PERCENT_30", from,
                                from.plus(1, ChronoUnit.DAYS), "HUMAN", "No rule", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn();

        assertThat(result.getHandler())
                .as("POST /api/markdown-plans must be handled by a controller method. A null or "
                        + "non-HandlerMethod handler means the endpoint is simply not mapped, and "
                        + "the 404 above is Spring's no-such-route answer rather than the "
                        + "contract's no-price-rule answer.")
                .isInstanceOf(HandlerMethod.class);
        assertThat(markdownEvents.published()).isEmpty();
    }

    // -------------------------------------------- GET /api/markdown-plans/{id}

    @Test
    @DisplayName("GET /api/markdown-plans/{id} -> 404 for an unknown plan, from a real handler")
    void getPlan_whenPlanDoesNotExist_returns404FromAControllerNotFromAMissingMapping() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/markdown-plans/{id}", "MDP-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getHandler())
                .as("GET /api/markdown-plans/{id} must be handled by a controller method. A null "
                        + "or non-HandlerMethod handler means the endpoint is simply not mapped, "
                        + "and the 404 above is Spring's no-such-route answer rather than the "
                        + "contract's no-such-plan answer.")
                .isInstanceOf(HandlerMethod.class);
    }

    // ---------------------------------------- FR8: the live markdown reaches GET /api/prices

    /**
     * FR8's extended criterion (4 October 2026): "when a markdown plan is ACTIVE inside its window,
     * the discounted effectivePrice and the activeDiscountTier". The SKU is created here with the
     * seeded yoghurt's economics so that the numbers are the same ones spec-03 §3 works through,
     * without putting a live markdown on the seeded SKU that other tests read.
     */
    @Test
    @DisplayName("GET /api/prices/{sku} reflects a markdown whose window contains now (FR8 + FR9)")
    void getPrice_withAMarkdownInForce_showsTheDiscountedPriceAndTier() throws Exception {
        String sku = "SKU-MDP-LIVE";
        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "basePrice": { "amount": 4.50, "currency": "AUD" },
                                  "unitCost": { "amount": 2.10, "currency": "AUD" },
                                  "category": "DAIRY",
                                  "marginFloorRatio": 0.15
                                }"""))
                .andExpect(status().isCreated());

        Instant from = now().minus(1, ChronoUnit.HOURS);
        Instant to = now().plus(1, ChronoUnit.HOURS);
        postPlan(createPlanRequest("BAT-LIVE", sku, "PERCENT_30", from, to, "HUMAN",
                "Live now", null), 201);

        mockMvc.perform(get("/api/prices/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePrice.amount").value(4.50))
                .andExpect(jsonPath("$.effectivePrice.amount").value(3.15))
                .andExpect(jsonPath("$.effectivePrice.currency").value("AUD"))
                .andExpect(jsonPath("$.activeDiscountTier").value("PERCENT_30"));
    }
}
