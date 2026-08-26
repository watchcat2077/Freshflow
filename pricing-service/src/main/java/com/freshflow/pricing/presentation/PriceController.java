package com.freshflow.pricing.presentation;

import com.freshflow.pricing.application.PriceApplicationService;
import com.freshflow.pricing.application.PriceView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer (spec-01 &sect;2.1) &mdash; spec-04's {@code /api/prices} endpoint.
 *
 * <p>Translates only. The margin floor it returns is read from the domain; it is not computed,
 * checked or interpreted here (spec-01 &sect;2.5).
 */
@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceApplicationService priceApplicationService;

    public PriceController(PriceApplicationService priceApplicationService) {
        this.priceApplicationService = priceApplicationService;
    }

    /** FR8 &mdash; spec-04 {@code getPrice}. */
    @GetMapping("/{sku}")
    public ResponseEntity<PriceResponseDto> getPrice(@PathVariable String sku) {
        return priceApplicationService.findPrice(sku)
                .map(view -> ResponseEntity.ok(toResponse(view)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private PriceResponseDto toResponse(PriceView view) {
        return new PriceResponseDto(
                view.sku(),
                view.category(),
                new MoneyDto(view.basePriceAmount(), view.basePriceCurrency()),
                new MoneyDto(view.unitCostAmount(), view.unitCostCurrency()),
                new MoneyDto(view.effectivePriceAmount(), view.effectivePriceCurrency()),
                view.marginFloorRatio(),
                view.activeDiscountTier());
    }
}
