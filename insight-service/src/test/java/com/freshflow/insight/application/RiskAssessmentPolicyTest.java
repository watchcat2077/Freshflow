package com.freshflow.insight.application;

import com.freshflow.insight.domain.BatchRiskView;
import com.freshflow.insight.domain.RiskAssessmentPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * FR13's core judgement: "computes projected sales over remaining trading hours and compares
 * against remaining quantity".
 *
 * <p>No Spring, no broker, no clock. Every instant is passed in, which is what lets the arithmetic
 * be stated exactly rather than approximately.
 *
 * <p><b>Expected to FAIL when written</b> — the policy throws
 * {@code UnsupportedOperationException} until the implementation lands.
 */
@DisplayName("FR13: risk assessment policy")
class RiskAssessmentPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final String BATCH = "BAT-0001";
    private static final String SKU = "SKU-YOG-1KG";

    /** Threshold 5 units; every remaining hour is a trading hour. */
    private RiskAssessmentPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RiskAssessmentPolicy(5.0, 24.0);
    }

    private Instant expiryIn(long hours) {
        return NOW.plus(hours, ChronoUnit.HOURS);
    }

    /**
     * 40 units, 10 hours left, selling 2 an hour. Projected sales 20, so 20 units will still be
     * on the shelf when it expires. Well over the threshold of 5: at risk.
     */
    @Test
    @DisplayName("flags a batch that will not clear before expiry")
    void flagsABatchThatWillNotClear() {
        BatchRiskView view = policy.assess(BATCH, SKU, 40, expiryIn(10), 2.0, NOW);

        assertThat(view.batchId()).isEqualTo(BATCH);
        assertThat(view.sku()).isEqualTo(SKU);
        assertThat(view.remainingQuantity()).isEqualTo(40);
        assertThat(view.hoursToExpiry()).isEqualTo(10);
        assertThat(view.currentVelocity()).isEqualTo(2.0);
        assertThat(view.projectedSales()).isCloseTo(20.0, within(0.0001));
        assertThat(view.projectedShortfall()).isCloseTo(20.0, within(0.0001));
        assertThat(view.detectedAt()).isEqualTo(NOW);
        assertThat(view.atRisk()).isTrue();
    }

    /**
     * The same batch selling fast enough. 40 units, 10 hours, 5 an hour: projected sales 50,
     * shortfall -10, meaning it clears with room to spare. Not a risk, and the negative shortfall
     * is reported rather than clamped, because "will clear comfortably" and "will just clear" are
     * different facts.
     */
    @Test
    @DisplayName("does not flag a batch that will clear in time")
    void doesNotFlagABatchThatWillClear() {
        BatchRiskView view = policy.assess(BATCH, SKU, 40, expiryIn(10), 5.0, NOW);

        assertThat(view.projectedSales()).isCloseTo(50.0, within(0.0001));
        assertThat(view.projectedShortfall()).isCloseTo(-10.0, within(0.0001));
        assertThat(view.atRisk()).isFalse();
    }

    /**
     * The threshold exists so that a batch missing by a hair is not an alarm. 24 units, 10 hours,
     * 2 an hour gives a shortfall of exactly 4 — under the threshold of 5, so no alarm.
     */
    @Test
    @DisplayName("stays quiet when the shortfall is below the configured threshold")
    void staysQuietBelowTheThreshold() {
        BatchRiskView view = policy.assess(BATCH, SKU, 24, expiryIn(10), 2.0, NOW);

        assertThat(view.projectedShortfall()).isCloseTo(4.0, within(0.0001));
        assertThat(view.atRisk()).isFalse();
    }

    /**
     * A SKU that has stopped selling entirely is the clearest risk there is: nothing will clear.
     * Zero velocity must not be mistaken for missing data.
     */
    @Test
    @DisplayName("flags a batch whose SKU has stopped selling")
    void flagsAStalledSku() {
        BatchRiskView view = policy.assess(BATCH, SKU, 30, expiryIn(8), 0.0, NOW);

        assertThat(view.projectedSales()).isCloseTo(0.0, within(0.0001));
        assertThat(view.projectedShortfall()).isCloseTo(30.0, within(0.0001));
        assertThat(view.atRisk()).isTrue();
    }

    /**
     * Trading hours are not clock hours. With the shop open 12 hours a day, 24 hours to expiry is
     * only 12 selling hours, so the same velocity projects half the sales — and a batch that looks
     * safe against clock hours is revealed as at risk.
     */
    @Test
    @DisplayName("projects over trading hours, not clock hours")
    void projectsOverTradingHoursOnly() {
        RiskAssessmentPolicy halfDay = new RiskAssessmentPolicy(5.0, 12.0);

        BatchRiskView view = halfDay.assess(BATCH, SKU, 30, expiryIn(24), 1.0, NOW);

        assertThat(view.hoursToExpiry()).as("hoursToExpiry is still real elapsed time").isEqualTo(24);
        assertThat(view.projectedSales()).as("but only half of them are selling hours")
                .isCloseTo(12.0, within(0.0001));
        assertThat(view.projectedShortfall()).isCloseTo(18.0, within(0.0001));
        assertThat(view.atRisk()).isTrue();
    }

    /**
     * An already-expired batch cannot be saved by a markdown, and FR13 exists to catch batches
     * "early enough that a markdown can still change the outcome". Reporting it as an actionable
     * risk would be advice that arrives too late to act on; FR3's write-off is the answer to an
     * expired batch, not a markdown.
     */
    @Test
    @DisplayName("does not flag a batch that has already expired")
    void doesNotFlagAnExpiredBatch() {
        BatchRiskView view = policy.assess(BATCH, SKU, 30, NOW.minus(1, ChronoUnit.HOURS), 1.0, NOW);

        assertThat(view.hoursToExpiry()).isLessThanOrEqualTo(0);
        assertThat(view.atRisk()).isFalse();
    }

    /** Nothing left to sell is nothing to worry about, however slowly the SKU is moving. */
    @Test
    @DisplayName("does not flag a depleted batch")
    void doesNotFlagADepletedBatch() {
        BatchRiskView view = policy.assess(BATCH, SKU, 0, expiryIn(4), 0.0, NOW);

        assertThat(view.remainingQuantity()).isZero();
        assertThat(view.atRisk()).isFalse();
    }
}
