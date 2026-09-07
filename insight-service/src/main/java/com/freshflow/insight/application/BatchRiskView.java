package com.freshflow.insight.application;

import java.time.Instant;

/**
 * spec-03 &sect;4 &mdash; {@code BatchRiskView}. The CQRS read model behind
 * {@code GET /api/insight/risks} (FR13, spec-04), and the payload the topology stores.
 *
 * <p>Every field except the last is spec-03 &sect;4's, in its order. {@code atRisk} is an addition:
 * the topology evaluates every batch it sees, not only the alarming ones, and the flag is what
 * separates the two. Storing the safe ones as well is deliberate &mdash; a batch that has stopped
 * being at risk must be able to say so, and a view that only ever held alarms could never
 * distinguish "recovered" from "never evaluated".
 *
 * <p>{@code detectedAt} is <b>event time</b>: the end of the velocity window the assessment was
 * made from, not the wall clock at the moment of assessment. Every other time-derived field here
 * &mdash; {@code hoursToExpiry} above all &mdash; is measured from that same instant, so the whole
 * view describes one consistent moment (spec-01 &sect;2.6).
 *
 * <p><b>{@code projectedSales} inherits ADR 0003's caveat.</b> It is velocity multiplied by the
 * remaining trading hours, and velocity is compared against an asserted baseline rather than a
 * measured one. The projection is arithmetic on a real measurement; the <i>ratio</i> reported
 * beside it in FR12 is not.
 */
public record BatchRiskView(String batchId,
                            String sku,
                            int remainingQuantity,
                            long hoursToExpiry,
                            double currentVelocity,
                            double projectedSales,
                            double projectedShortfall,
                            Instant detectedAt,
                            boolean atRisk) {
}
