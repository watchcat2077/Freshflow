package com.freshflow.insight.application;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Application service &mdash; the read-side use case behind {@code GET /api/insight/risks} (FR13,
 * spec-04: "List batches currently flagged as at risk of expiring unsold").
 *
 * <p>It reads what the topology already decided and reports the flagged ones. It does not
 * re-assess: the threshold was applied once, by {@link RiskAssessmentPolicy}, at the moment the
 * evidence arrived, and re-applying it here against a different instant could contradict the alarm
 * that was already published on {@code sell-through-risk-detected}.
 */
@Service
public class RiskQueryService {

    private final RiskViewStore riskViewStore;

    public RiskQueryService(RiskViewStore riskViewStore) {
        this.riskViewStore = riskViewStore;
    }

    /**
     * The batches currently flagged, worst first &mdash; largest projected shortfall at the top,
     * because that is the one a manager should mark down first.
     */
    public List<BatchRiskView> listCurrentRisks() {
        return riskViewStore.findAll().stream()
                .filter(BatchRiskView::atRisk)
                .sorted(Comparator.comparingDouble(BatchRiskView::projectedShortfall).reversed())
                .toList();
    }
}
