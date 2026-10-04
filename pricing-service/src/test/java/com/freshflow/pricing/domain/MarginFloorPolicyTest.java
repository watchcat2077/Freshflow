package com.freshflow.pricing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR11's margin floor rule, tested where it lives: the domain service (spec-03 §3 — "Spans
 * {@code PriceRule} and {@code MarkdownPlan}, therefore modelled as a domain service. This is the
 * enforcement point of the Anti-Corruption Layer").
 *
 * <p>No Spring, no broker, no database. {@link MarginFloorPolicy} is a POJO and this is a plain
 * JUnit test, which is the practical dividend of spec-01 §2.3's insistence that domain classes
 * import nothing.
 *
 * <p>The four worked cases are the ones team ruling B-3 (4 October 2026) wrote into spec-03 §3 and
 * spec-04's {@code createMarkdownPlan} description. They are asserted to the cent and to four
 * decimal places, because those are the numbers spec-04's {@code MarginFloorRejection} reports
 * and nothing outside the domain may recompute them.
 *
 * <p><b>Expected to FAIL when written</b> — none of the types under test exist yet.
 */
@DisplayName("FR11: margin floor policy")
class MarginFloorPolicyTest {

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

    // ------------------------------------------------------------ the ruled cases

    @ParameterizedTest(name = "{0} -> {1}, margin {2}, breached={3}")
    @CsvSource({
            "PERCENT_20, 3.60,  0.4167, false",
            "PERCENT_30, 3.15,  0.3333, false",
            "PERCENT_50, 2.25,  0.0667, true",
            "PERCENT_60, 1.80, -0.1667, true"
    })
    @DisplayName("assesses the four spec-03 s3 cases against the seeded SKU")
    void assessesTheSeededCases(String tier, String resultingPrice, String ratio, boolean breached) {
        MarginFloorAssessment assessment = policy.assess(seededYoghurt(), DiscountTier.fromName(tier));

        assertThat(assessment.resultingPrice()).isEqualTo(money(resultingPrice));
        assertThat(assessment.resultingMarginRatio())
                .as("gross margin as a fraction of the selling price, scale 4, HALF_UP")
                .isEqualTo(new BigDecimal(ratio));
        assertThat(assessment.resultingMarginRatio().scale()).isEqualTo(4);
        assertThat(assessment.breached()).isEqualTo(breached);
    }

    @Test
    @DisplayName("carries the inputs it was computed from, so the 422 body needs nothing else")
    void carriesItsInputs() {
        PriceRule rule = seededYoghurt();

        MarginFloorAssessment assessment = policy.assess(rule, DiscountTier.PERCENT_60);

        assertThat(assessment.basePrice()).isEqualTo(rule.basePrice());
        assertThat(assessment.unitCost()).isEqualTo(rule.unitCost());
        assertThat(assessment.requestedTier()).isEqualTo(DiscountTier.PERCENT_60);
        assertThat(assessment.floor()).isEqualTo(rule.marginFloor());
    }

    // ------------------------------------------------------------------ message

    @Test
    @DisplayName("renders the breach exactly as spec-03 s3 and spec-04 illustrate it")
    void rendersTheBreachMessage() {
        MarginFloorAssessment assessment = policy.assess(seededYoghurt(), DiscountTier.PERCENT_60);

        assertThat(assessment.message())
                .isEqualTo("Discount tier PERCENT_60 yields margin -0.1667, below floor 0.15");
    }

    /**
     * The floor comes back from its {@code precision 5, scale 4} column as {@code 0.1500}.
     * spec-03 §7 keeps {@code MarginFloor} out of the monetary normalisation, so the rendering is
     * the message's job: a floor of 0.15 reads as 0.15 whichever path produced it.
     */
    @Test
    @DisplayName("renders a floor read back at column scale without trailing zeros")
    void rendersAFloorAtColumnScaleCleanly() {
        PriceRule rule = PriceRule.of(Sku.of("SKU-YOG-1KG"), money("4.50"), money("2.10"), "DAIRY",
                MarginFloor.of(new BigDecimal("0.1500")));

        MarginFloorAssessment assessment = policy.assess(rule, DiscountTier.PERCENT_50);

        assertThat(assessment.message())
                .isEqualTo("Discount tier PERCENT_50 yields margin 0.0667, below floor 0.15");
    }

    // ----------------------------------------------------------------- arithmetic

    /**
     * spec-04: "resultingPrice = basePrice x (1 - tier/100), rounded half-up to cents". 2.05 at
     * fifty percent is 1.025, which HALF_UP takes to 1.03 and HALF_EVEN would have taken to 1.02.
     */
    @Test
    @DisplayName("rounds the discounted price half-up to cents, per spec-03 s7")
    void roundsHalfUpToCents() {
        PriceRule rule = PriceRule.of(Sku.of("SKU-ROUND"), money("2.05"), money("1.00"), "TEST",
                MarginFloor.of(new BigDecimal("0.10")));

        MarginFloorAssessment assessment = policy.assess(rule, DiscountTier.PERCENT_50);

        assertThat(assessment.resultingPrice()).isEqualTo(money("1.03"));
        // (1.03 - 1.00) / 1.03 = 0.029126... -> 0.0291
        assertThat(assessment.resultingMarginRatio()).isEqualTo(new BigDecimal("0.0291"));
    }

    /** "at or above the margin floor" (spec-03 §3 invariant 1): meeting it exactly is not a breach. */
    @Test
    @DisplayName("a margin exactly on the floor is not a breach")
    void aMarginExactlyOnTheFloorPasses() {
        // 4.00 at twenty percent is 3.20; (3.20 - 2.72) / 3.20 = 0.15 exactly.
        PriceRule rule = PriceRule.of(Sku.of("SKU-EDGE"), money("4.00"), money("2.72"), "TEST",
                MarginFloor.of(new BigDecimal("0.15")));

        MarginFloorAssessment assessment = policy.assess(rule, DiscountTier.PERCENT_20);

        assertThat(assessment.resultingMarginRatio()).isEqualTo(new BigDecimal("0.1500"));
        assertThat(assessment.breached()).isFalse();
    }

    @Test
    @DisplayName("the discounted price is the rule's own arithmetic, stated once")
    void discountedPriceIsTheRulesArithmetic() {
        PriceRule rule = seededYoghurt();

        for (DiscountTier tier : DiscountTier.values()) {
            assertThat(policy.assess(rule, tier).resultingPrice())
                    .as("resultingPrice for %s", tier)
                    .isEqualTo(rule.priceUnder(tier));
        }
    }
}
