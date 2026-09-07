package com.freshflow.insight.presentation;

import com.freshflow.insight.application.SkuVelocityView;
import com.freshflow.insight.application.VelocityQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * spec-04 {@code getVelocity} &mdash; {@code GET /api/insight/velocity/&#123;sku&#125;} (FR12).
 *
 * <p>Presentation only: it maps a read model to a DTO and an absent read model to 404. It decides
 * nothing, computes nothing, and never touches a state store. Which window counts as current and
 * how the ratio is derived are the application service's business (spec-01 &sect;2.5 forbids
 * putting rules here in every service, under every pattern).
 */
@RestController
@RequestMapping("/api/insight/velocity")
public class VelocityController {

    private final VelocityQueryService velocityQueryService;

    public VelocityController(VelocityQueryService velocityQueryService) {
        this.velocityQueryService = velocityQueryService;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<VelocityResponseDto> getVelocity(@PathVariable String sku) {
        return velocityQueryService.findCurrentVelocity(sku)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                // spec-04: "404 - No windowed data for this SKU yet."
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private VelocityResponseDto toDto(SkuVelocityView view) {
        return new VelocityResponseDto(
                view.sku(),
                view.windowStart(),
                view.windowEnd(),
                view.quantitySold(),
                view.velocityPerHour(),
                view.baselineVelocity(),
                view.velocityRatio());
    }
}
