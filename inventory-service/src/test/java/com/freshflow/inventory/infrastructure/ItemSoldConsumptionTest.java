package com.freshflow.inventory.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshflow.contracts.ItemSoldEvent;
import com.freshflow.contracts.MoneyPayload;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR2 — deduct stock on sale, end to end through the inbound adapter.
 *
 * <p>Batches are created through {@code POST /api/batches}, because spec-04 defines no other way
 * to create one and going around the API would test less than the contract promises. Remaining
 * quantities are read back through {@code GET /api/batches/&#123;batchId&#125;} for the same
 * reason. Only the Kafka transport is bypassed: {@link ItemSoldMessageHandler} is invoked
 * directly, which is exactly why the logic lives in it rather than in the binding.
 *
 * <p><b>Expected to FAIL when written</b> — the handler and the FEFO policy both throw
 * {@code UnsupportedOperationException} until Phase 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("FR2: inventory consumes item-sold and deducts FEFO")
class ItemSoldConsumptionTest {

    /**
     * Every test uses its own SKU. The Spring context -- and therefore the H2 instance -- is
     * shared across the whole class, so batches created by one test are still on hand during the
     * next. Since FEFO deliberately draws from any active batch of the SKU, a shared SKU would let
     * one test's leftover stock satisfy another test's sale, and the failures would look like FEFO
     * bugs rather than test bleed.
     */
    private static String skuFor(String test) {
        return "SKU-FR2-" + test;
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ItemSoldMessageHandler messageHandler;

    @Autowired
    private InventoryTestMessagingConfiguration.RecordingDomainEventPublisher publisher;

    @BeforeEach
    void clearRecordedEvents() {
        publisher.clear();
    }

    /** Creates a batch through the API and returns its batchId. */
    private String createBatch(String sku, int quantity, long expiresInHours) throws Exception {
        String body = """
                {
                  "sku": "%s",
                  "quantity": %d,
                  "expiryAt": "%s",
                  "unitCost": { "amount": 2.10, "currency": "AUD" },
                  "supplierRef": "DEL-88213"
                }""".formatted(sku, quantity,
                Instant.now().plus(expiresInHours, ChronoUnit.HOURS).toString());

        MvcResult created = mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("batchId").asText();
    }

    private ItemSoldEvent sale(String sku, UUID eventId, int quantity) {
        Instant soldAt = Instant.now();
        return new ItemSoldEvent(eventId, soldAt, "TXN-TEST", sku, quantity,
                new MoneyPayload(new BigDecimal("4.50"), "AUD"), soldAt);
    }

    private int remainingQuantityOf(String batchId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("remainingQuantity").asInt();
    }

    // ------------------------------------------------------------------ FEFO

    /**
     * The core of FR2. Two batches of the same SKU, one expiring sooner; a sale of six against a
     * soonest-expiring batch of four must empty that batch and take the remaining two from the
     * other. Asserting both numbers is what distinguishes FEFO from any other allocation.
     */
    @Test
    @DisplayName("deducts FEFO across batches of the same SKU, soonest expiry first")
    void deductsFefoAcrossBatches() throws Exception {
        String sku = skuFor("FEFO");
        String soonest = createBatch(sku, 4, 6);
        String later = createBatch(sku, 10, 48);

        boolean applied = messageHandler.handle(sale(sku, UUID.randomUUID(), 6));

        assertThat(applied).isTrue();
        assertThat(remainingQuantityOf(soonest)).as("soonest-expiring batch is emptied first").isZero();
        assertThat(remainingQuantityOf(later)).as("remainder comes from the later batch").isEqualTo(8);
    }

    @Test
    @DisplayName("publishes one stock-level-changed per batch actually touched")
    void publishesStockLevelChangedPerTouchedBatch() throws Exception {
        String sku = skuFor("EVENTS");
        String soonest = createBatch(sku, 4, 6);
        String later = createBatch(sku, 10, 48);

        messageHandler.handle(sale(sku, UUID.randomUUID(), 6));

        assertThat(publisher.stockLevelChanges())
                .as("one event per batch drawn from, and none for batches left alone")
                .hasSize(2)
                .extracting(event -> event.batchId().value())
                .containsExactly(soonest, later);
        assertThat(publisher.stockLevelChanges())
                .extracting(event -> event.remainingQuantity().value())
                .containsExactly(0, 8);
    }

    /**
     * spec-01 §2.6: the event time is the producer's, not this consumer's. A sale that reaches
     * Inventory late still changed stock at the moment the sale happened, and FR13 windows on
     * that instant.
     */
    @Test
    @DisplayName("carries the sale's occurredAt onto stock-level-changed, not the time of consumption")
    void carriesTheSalesEventTimeThrough() throws Exception {
        String sku = skuFor("EVENTTIME");
        createBatch(sku, 10, 48);
        Instant soldAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        ItemSoldEvent lateArrival = new ItemSoldEvent(UUID.randomUUID(), soldAt, "TXN-LATE", sku, 2,
                new MoneyPayload(new BigDecimal("4.50"), "AUD"), soldAt);

        messageHandler.handle(lateArrival);

        assertThat(publisher.stockLevelChanges()).hasSize(1);
        assertThat(publisher.stockLevelChanges().get(0).changedAt())
                .as("changedAt is the sale's event time, half an hour ago, not now")
                .isEqualTo(soldAt);
    }

    // ------------------------------------------------------------ never negative

    /**
     * FR2: "Stock on hand must never become negative." The sale already happened, so the honest
     * response is to deduct everything there is rather than to refuse a fact or to invent stock.
     */
    @Test
    @DisplayName("never lets stock go negative when the sale exceeds available stock")
    void neverGoesNegative() throws Exception {
        String sku = skuFor("SHORTFALL");
        String only = createBatch(sku, 3, 6);

        messageHandler.handle(sale(sku, UUID.randomUUID(), 10));

        assertThat(remainingQuantityOf(only)).isZero();
    }

    // -------------------------------------------------------------- idempotency

    /**
     * FR2: "Idempotent with respect to {@code eventId}." Kafka delivers at least once, so the
     * same sale can arrive twice; deducting twice would silently understate stock, and FR13 would
     * then report risk on a batch that is fine.
     */
    @Test
    @DisplayName("applies the same eventId only once, however many times it is redelivered")
    void isIdempotentOnEventId() throws Exception {
        String sku = skuFor("IDEMPOTENT");
        String batchId = createBatch(sku, 10, 48);
        UUID eventId = UUID.randomUUID();

        boolean first = messageHandler.handle(sale(sku, eventId, 3));
        boolean second = messageHandler.handle(sale(sku, eventId, 3));
        boolean third = messageHandler.handle(sale(sku, eventId, 3));

        assertThat(first).as("first delivery is applied").isTrue();
        assertThat(second).as("redelivery is discarded").isFalse();
        assertThat(third).as("and stays discarded").isFalse();
        assertThat(remainingQuantityOf(batchId)).as("deducted exactly once").isEqualTo(7);
        assertThat(publisher.stockLevelChanges())
                .as("a discarded redelivery announces nothing")
                .hasSize(1);
    }

    @Test
    @DisplayName("two distinct sales of the same SKU both apply")
    void distinctEventIdsBothApply() throws Exception {
        String sku = skuFor("DISTINCT");
        String batchId = createBatch(sku, 10, 48);

        assertThat(messageHandler.handle(sale(sku, UUID.randomUUID(), 3))).isTrue();
        assertThat(messageHandler.handle(sale(sku, UUID.randomUUID(), 2))).isTrue();

        assertThat(remainingQuantityOf(batchId)).isEqualTo(5);
    }

    @Test
    @DisplayName("a sale of a SKU with no stock changes nothing and announces nothing")
    void unknownSkuIsHarmless() {
        Instant soldAt = Instant.now();
        ItemSoldEvent event = new ItemSoldEvent(UUID.randomUUID(), soldAt, "TXN-X", "SKU-NO-STOCK",
                1, new MoneyPayload(new BigDecimal("1.00"), "AUD"), soldAt);

        messageHandler.handle(event);

        assertThat(publisher.stockLevelChanges()).isEmpty();
    }
}
