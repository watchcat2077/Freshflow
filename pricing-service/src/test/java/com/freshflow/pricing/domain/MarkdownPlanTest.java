package com.freshflow.pricing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The five invariants of spec-03 §3, tested inside the aggregate that owns them (spec-01 §2.5:
 * "inside the aggregate, or in a domain service when the rule spans aggregates").
 *
 * <p>Invariant 1 is the Anti-Corruption Layer in miniature. The assessment that
 * {@link MarkdownPlan#activate} receives is produced by {@link MarginFloorPolicy}; the aggregate
 * decides from those numbers alone, records a refusal as a REJECTED plan, and throws. Nothing
 * here knows whether a human or the agent asked, which is the point: team ruling B-6 (4 October
 * 2026) chose this signature so the same method answers both.
 *
 * <p>No Spring, no database. <b>Expected to FAIL when written</b> — the aggregate and its value
 * objects do not exist yet.
 */
@DisplayName("FR9/FR11: MarkdownPlan aggregate")
class MarkdownPlanTest {

    private static final Instant NOW = Instant.parse("2026-10-04T09:00:00Z");
    private static final Instant FROM = NOW.plus(1, ChronoUnit.HOURS);
    private static final Instant TO = NOW.plus(25, ChronoUnit.HOURS);
    private static final String AUD = "AUD";

    private final MarginFloorPolicy policy = new MarginFloorPolicy();

    private static Money money(String amount) {
        return Money.of(new BigDecimal(amount), AUD);
    }

    /** The seeded SKU-YOG-1KG of spec-01 §2.7: base 4.50, cost 2.10, floor 0.15. */
    private static PriceRule seededYoghurt() {
        return PriceRule.of(Sku.of("SKU-YOG-1KG"), money("4.50"), money("2.10"), "DAIRY",
                MarginFloor.of(new BigDecimal("0.15")));
    }

    private MarkdownPlan proposed(DiscountTier tier, ProposalOrigin origin, String agentTraceId) {
        return MarkdownPlan.propose(
                MarkdownPlanId.newId(),
                BatchId.of("BAT-0001"),
                Sku.of("SKU-YOG-1KG"),
                tier,
                PromotionWindow.of(FROM, TO),
                origin,
                "41 units remaining, 9 trading hours to expiry, velocity 2.1/h",
                agentTraceId,
                NOW);
    }

    private MarkdownPlan proposed(DiscountTier tier) {
        return proposed(tier, ProposalOrigin.HUMAN, null);
    }

    /** PERCENT_30 against the seeded rule passes the floor (spec-03 §3: 3.15 and 0.3333). */
    private MarginFloorAssessment passing() {
        return policy.assess(seededYoghurt(), DiscountTier.PERCENT_30);
    }

    /** PERCENT_60 against the seeded rule breaches it (spec-03 §3: 1.80 and -0.1667). */
    private MarginFloorAssessment breaching() {
        return policy.assess(seededYoghurt(), DiscountTier.PERCENT_60);
    }

    // ------------------------------------------------------------------- propose

    @Test
    @DisplayName("a proposal starts PROPOSED with no price and no rejection")
    void proposeStartsProposed() {
        MarkdownPlan plan = proposed(DiscountTier.PERCENT_30, ProposalOrigin.AGENT, "TRC-0001");

        assertThat(plan.status()).isEqualTo(MarkdownStatus.PROPOSED);
        assertThat(plan.discountedPrice()).isNull();
        assertThat(plan.rejectionReason()).isNull();
        assertThat(plan.origin()).isEqualTo(ProposalOrigin.AGENT);
        assertThat(plan.agentTraceId()).isEqualTo("TRC-0001");
        assertThat(plan.batchId()).isEqualTo(BatchId.of("BAT-0001"));
        assertThat(plan.sku()).isEqualTo(Sku.of("SKU-YOG-1KG"));
        assertThat(plan.discountTier()).isEqualTo(DiscountTier.PERCENT_30);
        assertThat(plan.window()).isEqualTo(PromotionWindow.of(FROM, TO));
        assertThat(plan.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a new id carries the MDP- prefix spec-04 illustrates, with an 8-hex suffix")
    void newIdHasThePrefix() {
        assertThat(MarkdownPlanId.newId().value()).matches("MDP-[0-9A-F]{8}");
        assertThat(MarkdownPlanId.newId()).isNotEqualTo(MarkdownPlanId.newId());
    }

    // ------------------------------------------------- invariant 1: margin floor

    @Nested
    @DisplayName("invariant 1 - a plan may only be activated at or above the margin floor")
    class MarginFloor1 {

        @Test
        @DisplayName("activates on a passing assessment and takes its price")
        void activatesWhenTheFloorHolds() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);

            plan.activate(passing());

            assertThat(plan.status()).isEqualTo(MarkdownStatus.ACTIVE);
            assertThat(plan.discountedPrice()).isEqualTo(money("3.15"));
            assertThat(plan.rejectionReason()).isNull();
        }

        @Test
        @DisplayName("on a breach: records itself REJECTED with the assessment's message, then throws")
        void rejectsItselfAndThrowsOnABreach() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_60);
            MarginFloorAssessment assessment = breaching();

            assertThatThrownBy(() -> plan.activate(assessment))
                    .isInstanceOf(MarginFloorBreach.class)
                    .isInstanceOf(DomainRuleViolation.class)
                    .satisfies(thrown -> {
                        MarginFloorBreach breach = (MarginFloorBreach) thrown;
                        assertThat(breach.code()).isEqualTo("MARGIN_FLOOR_BREACH");
                        assertThat(breach.assessment()).isEqualTo(assessment);
                        assertThat(breach.getMessage()).isEqualTo(assessment.message());
                    });

            assertThat(plan.status()).isEqualTo(MarkdownStatus.REJECTED);
            assertThat(plan.rejectionReason())
                    .isEqualTo("Discount tier PERCENT_60 yields margin -0.1667, below floor 0.15");
            assertThat(plan.discountedPrice()).as("a rejected plan has no price").isNull();
        }

        /** The aggregate cannot recompute the numbers, but it can refuse numbers that are not its own. */
        @Test
        @DisplayName("refuses an assessment made for a different tier, and stays PROPOSED")
        void refusesAnAssessmentForAnotherTier() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_60);

            assertThatThrownBy(() -> plan.activate(passing()))
                    .isInstanceOf(DomainRuleViolation.class)
                    .isNotInstanceOf(MarginFloorBreach.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("ASSESSMENT_TIER_MISMATCH");

            assertThat(plan.status()).isEqualTo(MarkdownStatus.PROPOSED);
        }
    }

    // ---------------------------------------------------- invariant 2: window

    @Nested
    @DisplayName("invariant 2 - effectiveTo must be after effectiveFrom")
    class Window2 {

        @Test
        @DisplayName("an inverted window is refused")
        void invertedWindowIsRefused() {
            assertThatThrownBy(() -> PromotionWindow.of(TO, FROM))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("INVALID_WINDOW");
        }

        @Test
        @DisplayName("an empty window is refused")
        void emptyWindowIsRefused() {
            assertThatThrownBy(() -> PromotionWindow.of(FROM, FROM))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("INVALID_WINDOW");
        }

        @Test
        @DisplayName("contains its start and excludes its end")
        void containsIsHalfOpen() {
            PromotionWindow window = PromotionWindow.of(FROM, TO);

            assertThat(window.contains(FROM)).isTrue();
            assertThat(window.contains(FROM.plus(12, ChronoUnit.HOURS))).isTrue();
            assertThat(window.contains(FROM.minusMillis(1))).isFalse();
            assertThat(window.contains(TO)).isFalse();
        }
    }

    // ------------------------------------------------- invariant 3: transitions

    @Nested
    @DisplayName("invariant 3 - only PROPOSED may activate; only ACTIVE may expire")
    class Transitions3 {

        @Test
        @DisplayName("an ACTIVE plan cannot be activated again")
        void activeCannotBeActivatedAgain() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);
            plan.activate(passing());

            assertThatThrownBy(() -> plan.activate(passing()))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("PLAN_NOT_PROPOSED");
            assertThat(plan.status()).isEqualTo(MarkdownStatus.ACTIVE);
        }

        @Test
        @DisplayName("a PROPOSED plan cannot expire")
        void proposedCannotExpire() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);

            assertThatThrownBy(plan::expire)
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("PLAN_NOT_ACTIVE");
            assertThat(plan.status()).isEqualTo(MarkdownStatus.PROPOSED);
        }

        @Test
        @DisplayName("an ACTIVE plan expires, and an EXPIRED one cannot expire again or activate")
        void activeExpiresOnce() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);
            plan.activate(passing());

            plan.expire();

            assertThat(plan.status()).isEqualTo(MarkdownStatus.EXPIRED);
            assertThatThrownBy(plan::expire).isInstanceOf(DomainRuleViolation.class);
            assertThatThrownBy(() -> plan.activate(passing())).isInstanceOf(DomainRuleViolation.class);
            assertThat(plan.status()).isEqualTo(MarkdownStatus.EXPIRED);
        }
    }

    // --------------------------------------------- invariant 4: REJECTED is terminal

    @Nested
    @DisplayName("invariant 4 - a REJECTED plan is terminal")
    class Terminal4 {

        @Test
        @DisplayName("a plan rejected by the floor cannot be activated afterwards, even on a passing assessment")
        void rejectedByTheFloorStaysRejected() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_60);
            assertThatThrownBy(() -> plan.activate(breaching())).isInstanceOf(MarginFloorBreach.class);

            // A passing assessment for the plan's own tier would be needed to reactivate it at all;
            // what matters is that the terminal state is checked before anything else is.
            assertThatThrownBy(() -> plan.activate(breaching()))
                    .isInstanceOf(DomainRuleViolation.class)
                    .isNotInstanceOf(MarginFloorBreach.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("PLAN_REJECTED");
            assertThatThrownBy(plan::expire).isInstanceOf(DomainRuleViolation.class);
            assertThatThrownBy(() -> plan.reject("again")).isInstanceOf(DomainRuleViolation.class);
            assertThat(plan.status()).isEqualTo(MarkdownStatus.REJECTED);
        }

        @Test
        @DisplayName("reject(reason) records the reason on a PROPOSED plan, once")
        void rejectRecordsTheReason() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);

            plan.reject("Batch already written off");

            assertThat(plan.status()).isEqualTo(MarkdownStatus.REJECTED);
            assertThat(plan.rejectionReason()).isEqualTo("Batch already written off");
            assertThatThrownBy(() -> plan.reject("Second thoughts"))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("PLAN_REJECTED");
            assertThat(plan.rejectionReason()).isEqualTo("Batch already written off");
        }

        @Test
        @DisplayName("a rejection needs a reason: that is what makes it queryable")
        void rejectNeedsAReason() {
            MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);

            assertThatThrownBy(() -> plan.reject(" "))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("REJECTION_REASON_REQUIRED");
            assertThat(plan.status()).isEqualTo(MarkdownStatus.PROPOSED);
        }
    }

    // ------------------------------------------ invariant 5: origin is immutable

    /**
     * There is no behaviour to call here, which is the point: the invariant is that no behaviour
     * exists. The test therefore inspects the class — the field is final, and no instance method
     * accepts a {@link ProposalOrigin}. Only the static factories do.
     */
    @Test
    @DisplayName("invariant 5 - origin is immutable after creation")
    void originIsImmutable() throws NoSuchFieldException {
        Field origin = MarkdownPlan.class.getDeclaredField("origin");
        assertThat(Modifier.isFinal(origin.getModifiers())).as("origin field is final").isTrue();

        List<Method> originMutators = Arrays.stream(MarkdownPlan.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> Arrays.asList(method.getParameterTypes()).contains(ProposalOrigin.class))
                .toList();
        assertThat(originMutators).as("no instance method takes a ProposalOrigin").isEmpty();
    }

    // -------------------------------------------------------- in force / event

    @Test
    @DisplayName("is in force only while ACTIVE and inside its window")
    void isInForceOnlyWhenActiveAndInsideTheWindow() {
        MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);
        Instant inside = FROM.plus(2, ChronoUnit.HOURS);

        assertThat(plan.isInForceAt(inside)).as("PROPOSED is not in force").isFalse();

        plan.activate(passing());
        assertThat(plan.isInForceAt(inside)).isTrue();
        assertThat(plan.isInForceAt(FROM.minus(1, ChronoUnit.MINUTES))).as("before the window").isFalse();
        assertThat(plan.isInForceAt(TO)).as("at the end of the window").isFalse();

        plan.expire();
        assertThat(plan.isInForceAt(inside)).as("EXPIRED is not in force").isFalse();
    }

    @Test
    @DisplayName("an ACTIVE plan yields the markdown-applied event with the authorised price")
    void activePlanYieldsTheAppliedEvent() {
        MarkdownPlan plan = proposed(DiscountTier.PERCENT_30, ProposalOrigin.AGENT, "TRC-0001");
        plan.activate(passing());
        Instant occurredAt = NOW.plus(5, ChronoUnit.MINUTES);

        MarkdownApplied event = plan.toMarkdownAppliedEvent(occurredAt);

        assertThat(event.markdownPlanId()).isEqualTo(plan.markdownPlanId());
        assertThat(event.batchId()).isEqualTo(BatchId.of("BAT-0001"));
        assertThat(event.sku()).isEqualTo(Sku.of("SKU-YOG-1KG"));
        assertThat(event.discountTier()).isEqualTo(DiscountTier.PERCENT_30);
        assertThat(event.window()).isEqualTo(PromotionWindow.of(FROM, TO));
        assertThat(event.discountedPrice()).isEqualTo(money("3.15"));
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("a plan that is not ACTIVE has no markdown-applied event to give")
    void onlyAnActivePlanHasAnAppliedEvent() {
        MarkdownPlan plan = proposed(DiscountTier.PERCENT_30);

        assertThatThrownBy(() -> plan.toMarkdownAppliedEvent(NOW))
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).code())
                .isEqualTo("PLAN_NOT_ACTIVE");
    }

    // ------------------------------------------------------------ value objects

    @Test
    @DisplayName("DiscountTier parses the four spec-04 names and refuses anything else")
    void discountTierParsesTheContractNames() {
        assertThat(DiscountTier.fromName("PERCENT_20").percent()).isEqualTo(20);
        assertThat(DiscountTier.fromName("PERCENT_30").percent()).isEqualTo(30);
        assertThat(DiscountTier.fromName("PERCENT_50").percent()).isEqualTo(50);
        assertThat(DiscountTier.fromName("PERCENT_60").percent()).isEqualTo(60);

        for (String bad : new String[] {"PERCENT_45", "half price", "", null}) {
            assertThatThrownBy(() -> DiscountTier.fromName(bad))
                    .as("fromName(%s)", bad)
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(e -> ((DomainRuleViolation) e).code())
                    .isEqualTo("UNKNOWN_TIER");
        }
    }

    @Test
    @DisplayName("ProposalOrigin parses HUMAN and AGENT and refuses anything else")
    void proposalOriginParsesTheContractNames() {
        assertThat(ProposalOrigin.fromName("HUMAN")).isEqualTo(ProposalOrigin.HUMAN);
        assertThat(ProposalOrigin.fromName("AGENT")).isEqualTo(ProposalOrigin.AGENT);
        assertThatThrownBy(() -> ProposalOrigin.fromName("ROBOT"))
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(e -> ((DomainRuleViolation) e).code())
                .isEqualTo("UNKNOWN_ORIGIN");
    }

    @Test
    @DisplayName("BatchId and MarkdownPlanId refuse blank values")
    void identifiersRefuseBlanks() {
        assertThatThrownBy(() -> BatchId.of(" ")).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> MarkdownPlanId.of("")).isInstanceOf(DomainRuleViolation.class);
    }
}
