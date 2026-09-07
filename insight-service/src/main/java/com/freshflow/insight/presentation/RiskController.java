package com.freshflow.insight.presentation;

import com.freshflow.insight.application.BatchRiskView;
import com.freshflow.insight.application.RiskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * spec-04 {@code listRisks} &mdash; {@code GET /api/insight/risks} (FR13).
 *
 * <p>Presentation only: it maps read models to DTOs. Which batches are at risk, and in what order,
 * are the application service's business.
 *
 * <p>An empty list is a 200 with {@code []}, not a 404. spec-04 declares only a 200 for this
 * endpoint, and rightly: "nothing is at risk right now" is a successful and highly informative
 * answer, quite unlike the velocity endpoint's 404, which reports that no measurement exists.
 */
@RestController
@RequestMapping("/api/insight/risks")
public class RiskController {

    private final RiskQueryService riskQueryService;

    public RiskController(RiskQueryService riskQueryService) {
        this.riskQueryService = riskQueryService;
    }

    @GetMapping
    public List<RiskResponseDto> listRisks() {
        return riskQueryService.listCurrentRisks().stream()
                .map(this::toDto)
                .toList();
    }

    private RiskResponseDto toDto(BatchRiskView view) {
        return new RiskResponseDto(
                view.batchId(),
                view.sku(),
                view.remainingQuantity(),
                view.hoursToExpiry(),
                view.currentVelocity(),
                view.projectedSales(),
                view.projectedShortfall(),
                view.detectedAt());
    }
}
