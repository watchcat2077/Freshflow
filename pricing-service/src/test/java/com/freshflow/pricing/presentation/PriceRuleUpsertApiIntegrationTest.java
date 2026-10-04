package com.freshflow.pricing.presentation;

import com.freshflow.pricing.domain.PriceRulePublished;
import com.freshflow.pricing.infrastructure.PricingTestMessagingConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code PUT /api/prices/&#123;sku&#125;} (operationId
 * {@code upsertPriceRule}, FR8 beyond the seeded minimum, added to spec-04 on 4 October 2026),
 * generated from the contract BEFORE implementation.
 *
 * <p>The contract: the SKU in the path is the identity; creating answers 201, replacing answers
 * 200; either way the rule in force is announced on {@code price-rule-published} so that
 * {@code sales-service}'s snapshot follows it (spec-01 §1.3); and the domain's own rules —
 * base price not below cost, floor inside [0, 1] — surface as 400.
 *
 * <p>Each test uses its own SKU. The Spring context is shared across the pricing test classes,
 * and the recording publisher already holds the seeded SKU's announcement from startup, so
 * assertions on publication filter by SKU rather than counting the whole list.
 *
 * <p><b>These tests are expected to FAIL until the endpoint exists.</b>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: pricing-service PUT /api/prices/{sku}")
class PriceRuleUpsertApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PricingTestMessagingConfiguration.RecordingPriceRuleEventPublisher priceRuleEvents;

    /** spec-04 {@code UpsertPriceRuleRequest}. */
    private static String upsertRequest(String basePrice, String unitCost, String category,
                                        String marginFloorRatio) {
        return """
                {
                  "basePrice": { "amount": %s, "currency": "AUD" },
                  "unitCost": { "amount": %s, "currency": "AUD" },
                  "category": "%s",
                  "marginFloorRatio": %s
                }""".formatted(basePrice, unitCost, category, marginFloorRatio);
    }

    private List<PriceRulePublished> announcementsFor(String sku) {
        return priceRuleEvents.published().stream()
                .filter(event -> event.sku().value().equals(sku))
                .toList();
    }

    @Test
    @DisplayName("PUT /api/prices/{sku} -> 201 creates the rule and announces it on price-rule-published")
    void putPrice_forNewSku_returns201AndAnnounces() throws Exception {
        String sku = "SKU-STRAW-250G";

        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertRequest("6.00", "3.00", "PRODUCE", "0.20")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // spec-04 PriceResponse
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.category").value("PRODUCE"))
                .andExpect(jsonPath("$.basePrice.amount").value(6.00))
                .andExpect(jsonPath("$.basePrice.currency").value("AUD"))
                .andExpect(jsonPath("$.unitCost.amount").value(3.00))
                .andExpect(jsonPath("$.effectivePrice.amount").value(6.00))
                .andExpect(jsonPath("$.marginFloorRatio").value(0.20))
                .andExpect(jsonPath("$.activeDiscountTier").doesNotExist());

        // spec-01 s2.6: the standing rule travels on price-rule-published, base price not effective.
        List<PriceRulePublished> announced = announcementsFor(sku);
        assertThat(announced).hasSize(1);
        assertThat(announced.get(0).basePrice().amount()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(announced.get(0).unitCost().amount()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(announced.get(0).category()).isEqualTo("PRODUCE");

        // And it is now readable through FR8's read path.
        mockMvc.perform(get("/api/prices/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePrice.amount").value(6.00))
                .andExpect(jsonPath("$.marginFloorRatio").value(0.20));
    }

    @Test
    @DisplayName("PUT /api/prices/{sku} -> 200 replaces an existing rule and announces it again")
    void putPrice_forExistingSku_returns200AndReplaces() throws Exception {
        String sku = "SKU-BLUE-125G";
        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertRequest("5.00", "2.50", "PRODUCE", "0.20")))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertRequest("5.50", "2.50", "PRODUCE", "0.25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.basePrice.amount").value(5.50))
                .andExpect(jsonPath("$.effectivePrice.amount").value(5.50))
                .andExpect(jsonPath("$.marginFloorRatio").value(0.25));

        // The replacement is the rule now in force, for this service and for sales-service alike.
        mockMvc.perform(get("/api/prices/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePrice.amount").value(5.50))
                .andExpect(jsonPath("$.marginFloorRatio").value(0.25));

        List<PriceRulePublished> announced = announcementsFor(sku);
        assertThat(announced).hasSize(2);
        assertThat(announced.get(1).basePrice().amount()).isEqualByComparingTo(new BigDecimal("5.50"));
    }

    @Test
    @DisplayName("PUT /api/prices/{sku} -> 400 when the base price is below unit cost (domain rule)")
    void putPrice_withBasePriceBelowCost_returns400() throws Exception {
        String sku = "SKU-LOSS-LEADER";

        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertRequest("2.00", "3.00", "PRODUCE", "0.20")))
                .andExpect(status().isBadRequest())
                // spec-04 ErrorResponse, carrying the domain's own code
                .andExpect(jsonPath("$.error").value("BASE_PRICE_BELOW_COST"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(announcementsFor(sku)).as("a refused rule is not announced").isEmpty();
        mockMvc.perform(get("/api/prices/{sku}", sku)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/prices/{sku} -> 400 when the margin floor is outside [0, 1] (domain rule)")
    void putPrice_withFloorOutOfRange_returns400() throws Exception {
        String sku = "SKU-GREEDY-FLOOR";

        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertRequest("6.00", "3.00", "PRODUCE", "1.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MARGIN_FLOOR_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(announcementsFor(sku)).isEmpty();
    }

    @Test
    @DisplayName("PUT /api/prices/{sku} -> 400 when a required field is missing")
    void putPrice_withMissingField_returns400() throws Exception {
        String sku = "SKU-NO-COST";

        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "basePrice": { "amount": 6.00, "currency": "AUD" },
                                  "category": "PRODUCE",
                                  "marginFloorRatio": 0.20
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(announcementsFor(sku)).isEmpty();
    }

    @Test
    @DisplayName("PUT /api/prices/{sku} -> 400 when base price and unit cost disagree on currency")
    void putPrice_withCurrencyMismatch_returns400() throws Exception {
        String sku = "SKU-MIXED-CURRENCY";

        mockMvc.perform(put("/api/prices/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "basePrice": { "amount": 6.00, "currency": "AUD" },
                                  "unitCost": { "amount": 3.00, "currency": "NZD" },
                                  "category": "PRODUCE",
                                  "marginFloorRatio": 0.20
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CURRENCY_MISMATCH"));

        assertThat(announcementsFor(sku)).isEmpty();
    }
}
