package com.freshflow.insight.application;

import java.time.Instant;

/**
 * FR13's arithmetic and its threshold, in one place.
 *
 * <p>"Computes projected sales over remaining trading hours and compares against remaining
 * quantity" &mdash; that sentence is this class. It is a plain object with no Spring annotation and
 * no Kafka import, constructed by a bean method in {@code infrastructure} with the configured
 * thresholds.
 *
 * <p><b>Why it is here and not in the topology.</b> Two callers need the same answer: the topology,
 * which assesses every batch as events arrive and emits {@code sell-through-risk-detected} when one
 * newly becomes at risk, and the read side, which serves {@code GET /api/insight/risks}. If the
 * threshold lived in the topology and the endpoint applied its own, the alarm and the list could
 * disagree &mdash; a batch could be reported at risk by one and safe by the other. Computing it
 * once and storing the result makes that impossible, and lets the rule be tested with no broker,
 * no Spring context and no clock.
 *
 * <p>This service has no {@code domain} package (spec-01 &sect;2.3), so {@code application} is the
 * innermost layer available and this is where such a rule belongs (&sect;2.5).
 */
public class RiskAssessmentPolicy {

    private static final double HOURS_PER_DAY = 24.0;

    private final double shortfallThreshold;
    private final double tradingHoursPerDay;

    /**
     * @param shortfallThreshold units of projected shortfall above which a batch is reported at
     *                           risk. Zero would flag a batch expected to miss clearing by a
     *                           single unit, which is noise rather than a signal
     * @param tradingHoursPerDay how many of each 24 hours the shop is actually open and selling.
     *                           FR13 says "remaining <i>trading</i> hours" and no specification
     *                           defines the trading day, so it is configuration; 24 means every
     *                           remaining hour is a selling hour
     */
    public RiskAssessmentPolicy(double shortfallThreshold, double tradingHoursPerDay) {
        this.shortfallThreshold = shortfallThreshold;
        this.tradingHoursPerDay = tradingHoursPerDay;
    }

    /**
     * Assesses one batch against the sell-through rate currently observed for its SKU.
     *
     * @param now the assessment instant, in <b>event time</b> &mdash; the end of the velocity
     *            window this rate came from, never a wall clock (spec-01 &sect;2.6)
     */
    public BatchRiskView assess(String batchId, String sku, int remainingQuantity, Instant expiryAt,
                                double velocityPerHour, Instant now) {
        throw new UnsupportedOperationException("FR13 is not implemented yet.");
    }
}
