package com.freshflow.pricing.presentation;

import com.freshflow.pricing.application.ApplicationResult;
import com.freshflow.pricing.application.PriceApplicationService;
import com.freshflow.pricing.application.PriceView;
import com.freshflow.pricing.application.UpsertPriceRuleCommand;
import com.freshflow.pricing.application.UpsertPriceRuleResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer (spec-01 &sect;2.1) &mdash; spec-04's {@code /api/prices} endpoints.
 *
 * <p>Translates only. The margin floor it returns is read from the domain; it is not computed,
 * checked or interpreted here (spec-01 &sect;2.5). The {@code 400} mapping is a translation of
 * {@link ApplicationResult}'s failure state, not a judgement about the request, and the choice
 * between {@code 201} and {@code 200} is read from the result rather than worked out here.
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

    /** FR8 &mdash; spec-04 {@code upsertPriceRule}: 201 when created, 200 when replaced. */
    @PutMapping("/{sku}")
    public ResponseEntity<?> upsertPrice(@PathVariable String sku,
                                         @RequestBody(required = false) UpsertPriceRuleRequestDto request) {
        ApplicationResult<UpsertPriceRuleResult> result =
                priceApplicationService.upsert(toCommand(sku, request));

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(result.errorCode(), result.errorMessage()));
        }
        return result.value()
                .map(upsert -> ResponseEntity
                        .status(upsert.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .body(toResponse(upsert.view())))
                .orElseThrow();
    }

    private static UpsertPriceRuleCommand toCommand(String sku, UpsertPriceRuleRequestDto request) {
        if (request == null) {
            return new UpsertPriceRuleCommand(sku, null, null, null, null, null, null);
        }
        MoneyDto basePrice = request.basePrice();
        MoneyDto unitCost = request.unitCost();
        return new UpsertPriceRuleCommand(
                sku,
                basePrice == null ? null : basePrice.amount(),
                basePrice == null ? null : basePrice.currency(),
                unitCost == null ? null : unitCost.amount(),
                unitCost == null ? null : unitCost.currency(),
                request.category(),
                request.marginFloorRatio());
    }

    private static PriceResponseDto toResponse(PriceView view) {
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
