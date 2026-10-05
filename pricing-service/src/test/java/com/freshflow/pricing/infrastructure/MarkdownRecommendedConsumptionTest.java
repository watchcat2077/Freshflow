package com.freshflow.pricing.infrastructure;

import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.MarkdownRecommendedEvent;
import com.freshflow.pricing.domain.MarkdownApplied;
import com.freshflow.pricing.domain.MarkdownPlan;
import com.freshflow.pricing.domain.MarkdownStatus;
import com.freshflow.pricing.domain.ProposalOrigin;
import com.freshflow.pricing.application.MarkdownPlanApplicationService;
import com.freshflow.pricing.infrastructure.repository.MarkdownPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR9 and FR11 through the <i>second</i> entry point: the {@code markdown-recommended} consumer.
 *
 * <p>spec-04 (4 October 2026): "the HTTP endpoint and the Kafka consumer are two entry points to
 * one application service and one domain rule." These tests prove the consumer half of that
 * sentence — the same margin arithmetic, the same REJECTED record, the same event out — plus the
 * two things only a consumer has to get right: it is idempotent on {@code agentTraceId}, and it
 * never throws on a refusal, because a refusal is a normal outcome of FR11 and a consumer that
 * threw on it would poison its own partition.
 *
 * <p>Only the Kafka transport is bypassed: {@link MarkdownRecommendedMessageHandler} is invoked
 * directly, which is exactly why the logic lives in it rather than in the binding. Same split as
 * {@code inventory-service}'s {@code ItemSoldConsumptionTest}.
 *
 * <p>Promotion windows are in the future so the seeded SKU's live price, which
 * {@code PriceApiIntegrationTest} asserts in the same shared context, is not disturbed.
 *
 * <p><b>Expected to FAIL when written</b> — the handler, the repository port and the domain types
 * do not exist yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("FR9/FR11: pricing consumes markdown-recommended")
class MarkdownRecommendedConsumptionTest {

    /** Seeded in application-test.yml: base 4.50, cost 2.10, floor 0.15. */
    private static final String SEEDED_SKU = "SKU-YOG-1KG";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarkdownRecommendedMessageHandler messageHandler;

    @Autowired
    private MarkdownPlanRepository markdownPlans;

    @Autowired
    private PricingTestMessagingConfiguration.RecordingMarkdownEventPublisher markdownEvents;

    @BeforeEach
    void clearRecordedEvents() {
        markdownEvents.clear();
    }

    /** Every test uses its own trace id, because the trace id is the idempotency key under test. */
    private static String traceFor(String test) {
        return "TRC-FR11-" + test + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static MarkdownRecommendedEvent recommendation(UUID eventId, String sku, DiscountTier tier,
                                                           String agentTraceId) {
        Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
        return new MarkdownRecommendedEvent(eventId, Instant.now(), "BAT-AGENT-0001", sku, tier,
                from, from.plus(36, ChronoUnit.HOURS),
                "41 units remaining, 9 trading hours to expiry, velocity 2.1/h", agentTraceId);
    }

    private List<MarkdownApplied> appliedFor(MarkdownPlan plan) {
        return markdownEvents.published().stream()
                .filter(event -> event.markdownPlanId().equals(plan.markdownPlanId()))
                .toList();
    }

    // ----------------------------------------------------------------- activation

    @Test
    @DisplayName("activates a recommendation within the floor as an AGENT plan and publishes markdown-applied")
    void activatesARecommendationWithinTheFloor() throws Exception {
        String trace = traceFor("ACTIVATE");

        boolean recorded = messageHandler.handle(
                recommendation(UUID.randomUUID(), SEEDED_SKU, DiscountTier.PERCENT_30, trace));

        assertThat(recorded).isTrue();
        MarkdownPlan plan = markdownPlans.findByAgentTraceId(trace).orElseThrow();
        assertThat(plan.status()).isEqualTo(MarkdownStatus.ACTIVE);
        assertThat(plan.origin()).isEqualTo(ProposalOrigin.AGENT);
        assertThat(plan.agentTraceId()).isEqualTo(trace);
        assertThat(plan.batchId().value()).isEqualTo("BAT-AGENT-0001");
        assertThat(plan.discountedPrice().amount()).isEqualByComparingTo(new BigDecimal("3.15"));
        assertThat(appliedFor(plan)).hasSize(1);

        // And the agent's plan is readable through spec-04's GET like any other.
        mockMvc.perform(get("/api/markdown-plans/{id}", plan.markdownPlanId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("AGENT"))
                .andExpect(jsonPath("$.agentTraceId").value(trace))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.discountedPrice.amount").value(3.15));
    }

    // --------------------------------------------------------------- idempotency

    /**
     * spec-01 §2.6: consumers deduplicate. The key here is the trace, not the {@code eventId}: a
     * redelivery of the same message and a re-publication under a fresh {@code eventId} both
     * describe the one deliberation FR14 recorded, and FR14's own idempotency (spec-06 §5) means
     * the agent will never make two for the same trigger.
     */
    @Test
    @DisplayName("records one plan per agentTraceId, however many times the recommendation arrives")
    void isIdempotentOnAgentTraceId() {
        String trace = traceFor("IDEMPOTENT");
        UUID eventId = UUID.randomUUID();

        boolean first = messageHandler.handle(
                recommendation(eventId, SEEDED_SKU, DiscountTier.PERCENT_30, trace));
        boolean redelivered = messageHandler.handle(
                recommendation(eventId, SEEDED_SKU, DiscountTier.PERCENT_30, trace));
        boolean republished = messageHandler.handle(
                recommendation(UUID.randomUUID(), SEEDED_SKU, DiscountTier.PERCENT_30, trace));

        assertThat(first).as("first delivery is recorded").isTrue();
        assertThat(redelivered).as("redelivery is skipped").isFalse();
        assertThat(republished).as("a re-publication for the same trace is skipped too").isFalse();

        MarkdownPlan plan = markdownPlans.findByAgentTraceId(trace).orElseThrow();
        assertThat(appliedFor(plan)).as("exactly one markdown-applied for the trace").hasSize(1);
    }

    // ------------------------------------------------------------------ rejection

    @Test
    @DisplayName("records a breaching recommendation as REJECTED, publishes nothing, and does not throw (FR11)")
    void recordsABreachAsRejectedWithoutThrowing() {
        String trace = traceFor("REJECT");

        boolean recorded = messageHandler.handle(
                recommendation(UUID.randomUUID(), SEEDED_SKU, DiscountTier.PERCENT_60, trace));

        assertThat(recorded).isTrue();
        MarkdownPlan plan = markdownPlans.findByAgentTraceId(trace).orElseThrow();
        assertThat(plan.status()).isEqualTo(MarkdownStatus.REJECTED);
        assertThat(plan.origin()).isEqualTo(ProposalOrigin.AGENT);
        assertThat(plan.rejectionReason())
                .isEqualTo("Discount tier PERCENT_60 yields margin -0.1667, below floor 0.15");
        assertThat(plan.discountedPrice()).isNull();
        assertThat(markdownEvents.published()).as("nothing reaches the till").isEmpty();
    }

    @Test
    @DisplayName("a rejected trace is also final: redelivering it records nothing more")
    void aRejectedTraceIsNotReassessed() {
        String trace = traceFor("REJECT-TWICE");
        messageHandler.handle(recommendation(UUID.randomUUID(), SEEDED_SKU, DiscountTier.PERCENT_60, trace));

        boolean again = messageHandler.handle(
                recommendation(UUID.randomUUID(), SEEDED_SKU, DiscountTier.PERCENT_60, trace));

        assertThat(again).isFalse();
        assertThat(markdownPlans.findByAgentTraceId(trace).orElseThrow().status())
                .isEqualTo(MarkdownStatus.REJECTED);
    }

    // ------------------------------------------------------------------ dropped

    @Test
    @DisplayName("a recommendation for a SKU with no price rule is dropped, not thrown")
    void dropsARecommendationForAnUnpricedSku() {
        String trace = traceFor("NO-RULE");

        boolean recorded = messageHandler.handle(
                recommendation(UUID.randomUUID(), "SKU-NO-PRICE-RULE", DiscountTier.PERCENT_30, trace));

        assertThat(recorded).isFalse();
        assertThat(markdownPlans.findByAgentTraceId(trace)).isEmpty();
        assertThat(markdownEvents.published()).isEmpty();
    }

    @Test
    @DisplayName("a recommendation with no agentTraceId is unauditable and is ignored")
    void ignoresARecommendationWithoutATrace() {
        boolean recorded = messageHandler.handle(
                recommendation(UUID.randomUUID(), SEEDED_SKU, DiscountTier.PERCENT_30, " "));

        assertThat(recorded).isFalse();
        assertThat(markdownEvents.published()).isEmpty();
    }

    @Test
    @DisplayName("a null event is ignored")
    void ignoresANullEvent() {
        assertThat(messageHandler.handle(null)).isFalse();
        assertThat(markdownEvents.published()).isEmpty();
    }

    // ------------------------------------------------------------- unbounded text

    /**
     * 5 October 2026: the agent's rationale is prose a language model writes, with no natural
     * upper bound, and it travels on the event into this context's {@code markdown_plan} row. The
     * rule here, documented on {@code fitRationale} and untested until a fixed-length column
     * refused a long text in insight-service that afternoon: the copy kept with the plan is cut
     * to {@code RATIONALE_MAX_LENGTH} with a {@code [truncated]} log line, the recommendation
     * itself is not lost, and the full text stays in the agent trace under the same
     * {@code agentTraceId}, which is the audit record.
     */
    @Test
    @DisplayName("keeps the first 2000 characters of a very long agent rationale and the plan itself")
    void keepsTheHeadOfAVeryLongRationaleAndThePlan() {
        String trace = traceFor("LONG");
        String longRationale = "With many units remaining and little time left the model explained itself. ".repeat(120); // ~9,000 chars
        Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
        MarkdownRecommendedEvent event = new MarkdownRecommendedEvent(UUID.randomUUID(), Instant.now(),
                "BAT-AGENT-0001", SEEDED_SKU, DiscountTier.PERCENT_30, from, from.plus(36, ChronoUnit.HOURS),
                longRationale, trace);

        assertThat(messageHandler.handle(event)).isTrue();

        MarkdownPlan plan = markdownPlans.findByAgentTraceId(trace).orElseThrow();
        assertThat(plan.status()).isEqualTo(MarkdownStatus.ACTIVE);
        assertThat(plan.rationale())
                .hasSize(MarkdownPlanApplicationService.RATIONALE_MAX_LENGTH)
                .isEqualTo(longRationale.substring(0, MarkdownPlanApplicationService.RATIONALE_MAX_LENGTH));
    }
}
