package com.freshflow.inventory.presentation;

import com.freshflow.inventory.application.ApplicationResult;
import com.freshflow.inventory.application.BatchApplicationService;
import com.freshflow.inventory.application.BatchView;
import com.freshflow.inventory.application.RegisterGoodsReceiptCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presentation layer (spec-01 &sect;2.1) &mdash; spec-04's {@code /api/batches} endpoints.
 *
 * <p><b>This class decides nothing.</b> spec-01 &sect;2.5 is categorical: "In no case may an
 * invariant live in a controller." Every method here does exactly three things &mdash; turn a
 * request DTO into a command, hand it to the application layer, and turn the typed result back into
 * an HTTP status and a body. There is no {@code if} in this file that inspects a business value.
 *
 * <p>The {@code 400} mapping is a translation of {@link ApplicationResult}'s failure state, not a
 * judgement about the request; the rule that produced that state was applied in the domain or, for
 * request shape, in the application layer (spec-01 &sect;1.1, finding M-9).
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchApplicationService batchApplicationService;

    public BatchController(BatchApplicationService batchApplicationService) {
        this.batchApplicationService = batchApplicationService;
    }

    /** FR1 &mdash; spec-04 {@code createBatch}. */
    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody(required = false) CreateBatchRequestDto request) {
        ApplicationResult<BatchView> result =
                batchApplicationService.registerGoodsReceipt(toCommand(request));

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(result.errorCode(), result.errorMessage()));
        }
        return result.value()
                .map(view -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(view)))
                .orElseThrow();
    }

    /** FR4 &mdash; spec-04 {@code getBatch}. */
    @GetMapping("/{batchId}")
    public ResponseEntity<BatchResponseDto> getBatch(@PathVariable String batchId) {
        return batchApplicationService.findBatch(batchId)
                .map(view -> ResponseEntity.ok(toResponse(view)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * FR4 &mdash; spec-04 {@code listBatchesBySku} (added 4 October 2026).
     *
     * <p>{@code required = false} is deliberate, not an oversight. With {@code required = true}
     * Spring would answer a missing {@code sku} itself, with its own body and without this class
     * ever running; spec-01 &sect;1.1 (finding M-9) puts that validation in the application layer
     * and its body in spec-04's {@code ErrorResponse}. So whatever arrived &mdash; including
     * nothing &mdash; is passed on, and the 400 below is a translation of the typed result that
     * comes back, exactly as in {@link #createBatch}.
     */
    @GetMapping
    public ResponseEntity<?> listBatchesBySku(@RequestParam(name = "sku", required = false) String sku) {
        ApplicationResult<List<BatchView>> result = batchApplicationService.findActiveBatches(sku);

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(result.errorCode(), result.errorMessage()));
        }
        return result.value()
                .map(views -> ResponseEntity.ok(views.stream().map(this::toResponse).toList()))
                .orElseThrow();
    }

    private RegisterGoodsReceiptCommand toCommand(CreateBatchRequestDto request) {
        if (request == null) {
            return new RegisterGoodsReceiptCommand(null, null, null, null, null, null);
        }
        MoneyDto unitCost = request.unitCost();
        return new RegisterGoodsReceiptCommand(
                request.sku(),
                request.quantity(),
                request.expiryAt(),
                unitCost == null ? null : unitCost.amount(),
                unitCost == null ? null : unitCost.currency(),
                request.supplierRef());
    }

    private BatchResponseDto toResponse(BatchView view) {
        return new BatchResponseDto(
                view.batchId(),
                view.sku(),
                view.receivedQuantity(),
                view.remainingQuantity(),
                view.expiryAt(),
                new MoneyDto(view.unitCostAmount(), view.unitCostCurrency()),
                view.status(),
                view.remainingShelfLifeHours());
    }
}
