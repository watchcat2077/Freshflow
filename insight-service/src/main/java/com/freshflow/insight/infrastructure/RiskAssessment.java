package com.freshflow.insight.infrastructure;

import com.freshflow.insight.domain.BatchRiskView;

/**
 * A stored risk assessment, plus the one thing the view itself cannot express: whether this
 * assessment is the moment the batch <b>became</b> at risk.
 *
 * <p>{@code sell-through-risk-detected} is a detection, so it must fire on the transition from
 * safe to at-risk and not on every re-assessment. With a window advancing every few seconds a
 * single slow-selling batch would otherwise emit an event several times a minute for as long as it
 * remained at risk, and the topic would carry alarm volume rather than alarm information.
 *
 * <p>The flag is computed by comparing against the previous assessment for the same batch, which
 * is exactly what a {@code reduce} over a stream grouped by {@code batchId} makes available.
 */
public record RiskAssessment(BatchRiskView view, boolean newlyAtRisk) {

    /** The first assessment a batch ever receives: becoming at risk is itself the transition. */
    public static RiskAssessment initial(BatchRiskView view) {
        return new RiskAssessment(view, view.atRisk());
    }

    /** A later assessment, judged against the one it replaces. */
    public RiskAssessment succeeding(RiskAssessment previous) {
        return new RiskAssessment(view, view.atRisk() && !previous.view().atRisk());
    }
}
