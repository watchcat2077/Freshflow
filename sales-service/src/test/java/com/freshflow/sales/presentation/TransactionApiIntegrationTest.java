package com.freshflow.sales.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshflow.sales.infrastructure.SalesTestMessagingConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.freshflow.sales.infrastructure.SalesTestMessagingConfiguration.MARKDOWN_EFFECTIVE_FROM;
import static com.freshflow.sales.infrastructure.SalesTestMessagingConfiguration.MARKDOWN_EFFECTIVE_TO;
import static com.freshflow.sales.infrastructure.SalesTestMessagingConfiguration.MARKED_DOWN_SKU;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code sales-service} endpoint of spec-04, generated from the contract
 * BEFORE implementation (Continuous Auto-Validation, Phase 3).
 *
 * <p>Covers {@code POST /api/transactions} (operationId {@code createTransaction}), which spec-04
 * ties to FR5, FR6a and, since 4 October 2026, FR6b.
 *
 * <p>The base price is read from {@code sales-service}'s own local {@code PriceSnapshot}, fed
 * through the {@code PriceSnapshotFeed} inbound port (spec-01 §1.3, spec-03 §2, ADR 0001). No call
 * to {@code pricing-service} is made or mocked, because none is permitted to exist.
 *
 * <p><b>Week 6 changed what is behind that port, and nothing else here.</b> The Week 5 adapter read
 * {@code freshflow.sales.price-snapshot-seed} from {@code application-test.yml}; the Week 6 adapter
 * materialises the snapshot from {@code price-rule-published}. The assertions below are unchanged,
 * which is the observable form of spec-01 §1.3's claim that the swap is a Ports and Adapters
 * substitution. The event that carries the same values is now handed to the real adapter by
 * {@code SalesTestMessagingConfiguration}.
 *
 * <p><b>FR6b added two tests and changed no earlier assertion.</b> The markdown is seeded on a
 * second SKU through the same adapter's {@code markdown-applied} ingest path, so the FR6a tests on
 * SKU-YOG-1KG still see a SKU with no markdown and still assert {@code markdownApplied} false.
 *
 * <p><b>These tests were written to FAIL before Phase 4 implemented the endpoint</b>, and the two
 * FR6b tests were written to fail before the transaction script applied a markdown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("spec-04: sales-service transaction API")
class TransactionApiIntegrationTest {

    /** Carried by the {@code price-rule-published} event fed in by SalesTestMessagingConfiguration. */
    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    /** {@code basePrice} on that event. The same 4.50 AUD the Week 5 static seed carried. */
    private static final double SEEDED_BASE_PRICE = 4.50;

    private static final String UNKNOWN_SKU = "SKU-NOT-IN-SNAPSHOT";

    /** FR6b: {@code basePrice} on the marked-down SKU's rule, and the discounted price on its markdown. */
    private static final double MARKED_DOWN_BASE_PRICE = 6.00;
    private static final double MARKED_DOWN_DISCOUNTED_PRICE = 4.20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SalesTestMessagingConfiguration.RecordingSaleEventPublisher saleEventPublisher;

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

    // ---------------------------------------------------- FR6b: markdown at point of scan

    /**
     * FR6b: "The price recorded on the line item is the price in force at {@code occurredAt}" and
     * "{@code markdownApplied} is true when a markdown was in force at {@code occurredAt}". The
     * transaction is stamped inside the seeded window, which closed an hour before this test ran:
     * it is a late-arriving scan, and the price it records is the one in force when it happened,
     * not the one in force when it was processed (spec-03 §2). Quantity 2 again, so that
     * {@code totalAmount} is checked against the discounted price and not merely against a
     * single line.
     *
     * <p>The transaction handed to the {@code SaleEventPublisher} port must carry the same
     * discounted price on its line, because that is what the Kafka adapter copies, line for line,
     * onto {@code item-sold}, and therefore what Inventory and Insight will see as the price
     * charged. The adapter itself is not bound in this profile; the transport is verified against
     * the real broker in the Week 6 log.
     */
    @Test
    @DisplayName("POST /api/transactions -> 201 with the discounted price inside the promotion window, "
            + "markdownApplied true, and item-sold carries that price (FR6b)")
    void postTransaction_insideMarkdownWindow_recordsDiscountedPriceAndPublishesIt() throws Exception {
        Instant insideWindow = MARKDOWN_EFFECTIVE_FROM.plus(1, ChronoUnit.HOURS);
        String body = """
                {
                  "tillId": "TILL-02",
                  "occurredAt": "%s",
                  "lineItems": [
                    { "sku": "%s", "quantity": 2 }
                  ]
                }""".formatted(insideWindow.toString(), MARKED_DOWN_SKU);

        MvcResult result = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.lineItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.lineItems[0].sku").value(MARKED_DOWN_SKU))
                // FR6b: the discounted price Pricing authorised, not the base price
                .andExpect(jsonPath("$.lineItems[0].unitPrice.amount").value(MARKED_DOWN_DISCOUNTED_PRICE))
                .andExpect(jsonPath("$.lineItems[0].unitPrice.currency").value("AUD"))
                .andExpect(jsonPath("$.lineItems[0].markdownApplied").value(true))
                .andExpect(jsonPath("$.totalAmount.amount").value(MARKED_DOWN_DISCOUNTED_PRICE * 2))
                .andReturn();

        String transactionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("transactionId").asText();
        var published = saleEventPublisher.published().stream()
                .filter(transaction -> transaction.transactionId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the transaction must have been handed to the SaleEventPublisher port"));
        assertThat(published.lineItems()).hasSize(1);
        assertThat(published.lineItems().get(0).unitPrice().amount())
                .as("item-sold carries the price actually charged, which is the discounted one")
                .isEqualByComparingTo("4.20");
        assertThat(published.lineItems().get(0).markdownApplied()).isTrue();
        assertThat(published.occurredAt())
                .as("spec-01 §2.6: the event time is the transaction's own, not the time of processing")
                .isEqualTo(insideWindow);
    }

    /**
     * FR6b: "A markdown is in force only inside its promotion window; a scan after
     * {@code effectiveTo} reverts to the base price without any further event." The seeded window
     * closed an hour ago and nothing has told {@code sales-service} so — no {@code markdown-expired}
     * is consumed (B-7, ruled 4 October 2026). The snapshot still carries the markdown; the scan
     * is simply outside it.
     */
    @Test
    @DisplayName("POST /api/transactions -> 201 with the base price after effectiveTo, markdownApplied false, "
            + "with no further event (FR6b)")
    void postTransaction_afterMarkdownWindow_revertsToBasePrice() throws Exception {
        Instant afterWindow = Instant.now();
        assertThat(afterWindow).as("the seeded window has closed").isAfter(MARKDOWN_EFFECTIVE_TO);
        String body = """
                {
                  "tillId": "TILL-02",
                  "occurredAt": "%s",
                  "lineItems": [
                    { "sku": "%s", "quantity": 1 }
                  ]
                }""".formatted(afterWindow.toString(), MARKED_DOWN_SKU);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineItems[0].sku").value(MARKED_DOWN_SKU))
                // FR6b: outside the window the base price is back
                .andExpect(jsonPath("$.lineItems[0].unitPrice.amount").value(MARKED_DOWN_BASE_PRICE))
                .andExpect(jsonPath("$.lineItems[0].markdownApplied").value(false))
                .andExpect(jsonPath("$.totalAmount.amount").value(MARKED_DOWN_BASE_PRICE));
    }
}
