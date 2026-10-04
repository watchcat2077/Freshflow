package com.freshflow.pricing.presentation;

import com.freshflow.pricing.application.CreateMarkdownPlanCommand;
import com.freshflow.pricing.application.MarginFloorRejectionView;
import com.freshflow.pricing.application.MarkdownPlanApplicationService;
import com.freshflow.pricing.application.MarkdownPlanOutcome;
import com.freshflow.pricing.application.MarkdownPlanView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer (spec-01 &sect;2.1) &mdash; spec-04's {@code /api/markdown-plans} endpoints.
 *
 * <p><b>This class decides nothing.</b> spec-01 &sect;2.5 is categorical: "In no case may an
 * invariant live in a controller." Each method turns a request DTO into a command, hands it to the
 * application layer, and turns the typed outcome back into an HTTP status and a body. The
 * {@code switch} below is exhaustive over a sealed type and is a translation table, not a
 * judgement: which branch an outcome lands in was settled in the domain. There is no {@code if}
 * in this file that inspects a business value.
 *
 * <p>spec-04 calls this endpoint "the Anti-Corruption Layer enforcement point". It is the point at
 * which a proposal <i>enters</i>; the enforcement itself happens two layers down, in
 * {@code MarkdownPlan.activate} against a {@code MarginFloorPolicy} assessment, and the
 * {@code markdown-recommended} consumer reaches the same code without passing through here.
 */
@RestController
@RequestMapping("/api/markdown-plans")
public class MarkdownPlanController {

    private final MarkdownPlanApplicationService markdownPlanApplicationService;

    public MarkdownPlanController(MarkdownPlanApplicationService markdownPlanApplicationService) {
        this.markdownPlanApplicationService = markdownPlanApplicationService;
    }

    /** FR9 + FR11 &mdash; spec-04 {@code createMarkdownPlan}. */
    @PostMapping
    public ResponseEntity<?> createMarkdownPlan(
            @RequestBody(required = false) CreateMarkdownPlanRequestDto request) {
        MarkdownPlanOutcome outcome = markdownPlanApplicationService.create(toCommand(request));

        return switch (outcome) {
            case MarkdownPlanOutcome.Activated activated ->
                    ResponseEntity.status(HttpStatus.CREATED).body(toResponse(activated.plan()));
            case MarkdownPlanOutcome.Rejected rejected ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(toRejection(rejected.rejection()));
            case MarkdownPlanOutcome.Invalid invalid ->
                    ResponseEntity.badRequest().body(new ErrorResponseDto(invalid.code(), invalid.message()));
            case MarkdownPlanOutcome.PriceRuleNotFound notFound ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponseDto(notFound.code(), notFound.message()));
        };
    }

    /** FR11's "queryable" &mdash; spec-04 {@code getMarkdownPlan}. */
    @GetMapping("/{markdownPlanId}")
    public ResponseEntity<MarkdownPlanResponseDto> getMarkdownPlan(@PathVariable String markdownPlanId) {
        return markdownPlanApplicationService.findPlan(markdownPlanId)
                .map(view -> ResponseEntity.ok(toResponse(view)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static CreateMarkdownPlanCommand toCommand(CreateMarkdownPlanRequestDto request) {
        if (request == null) {
            return new CreateMarkdownPlanCommand(null, null, null, null, null, null, null, null);
        }
        return new CreateMarkdownPlanCommand(
                request.batchId(),
                request.sku(),
                request.discountTier(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.origin(),
                request.rationale(),
                request.agentTraceId());
    }

    private static MarkdownPlanResponseDto toResponse(MarkdownPlanView view) {
        MoneyDto discountedPrice = view.discountedPriceAmount() == null
                ? null
                : new MoneyDto(view.discountedPriceAmount(), view.discountedPriceCurrency());
        return new MarkdownPlanResponseDto(
                view.markdownPlanId(),
                view.batchId(),
                view.sku(),
                view.discountTier(),
                view.effectiveFrom(),
                view.effectiveTo(),
                view.status(),
                view.origin(),
                discountedPrice,
                view.rationale(),
                view.agentTraceId(),
                view.rejectionReason(),
                view.createdAt());
    }

    private static MarginFloorRejectionDto toRejection(MarginFloorRejectionView rejection) {
        return new MarginFloorRejectionDto(
                MarginFloorRejectionDto.ERROR,
                rejection.markdownPlanId(),
                rejection.sku(),
                rejection.requestedTier(),
                new MoneyDto(rejection.resultingPriceAmount(), rejection.resultingPriceCurrency()),
                new MoneyDto(rejection.unitCostAmount(), rejection.unitCostCurrency()),
                rejection.resultingMarginRatio(),
                rejection.marginFloorRatio(),
                rejection.message());
    }
}
