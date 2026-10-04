package com.freshflow.sales.presentation;

import com.freshflow.sales.application.ApplicationResult;
import com.freshflow.sales.application.RecordTransactionCommand;
import com.freshflow.sales.application.TransactionApplicationService;
import com.freshflow.sales.application.TransactionView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presentation layer (spec-01 &sect;2.1) &mdash; spec-04's {@code /api/transactions} endpoint.
 *
 * <p>Translates only. Which price applies, whether a SKU is known, and whether a line item is
 * acceptable are all decided by the transaction script in the application layer, where spec-01
 * &sect;2.5 places this service's invariants. There is no business condition tested in this file.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionApplicationService transactionApplicationService;

    public TransactionController(TransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
    }

    /** FR5, FR6a and FR6b &mdash; spec-04 {@code createTransaction}. */
    @PostMapping
    public ResponseEntity<?> createTransaction(
            @RequestBody(required = false) CreateTransactionRequestDto request) {

        ApplicationResult<TransactionView> result =
                transactionApplicationService.recordTransaction(toCommand(request));

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(result.errorCode(), result.errorMessage()));
        }
        return result.value()
                .map(view -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(view)))
                .orElseThrow();
    }

    private RecordTransactionCommand toCommand(CreateTransactionRequestDto request) {
        if (request == null) {
            return null;
        }
        List<RecordTransactionCommand.Line> lines = request.lineItems() == null
                ? null
                : request.lineItems().stream()
                        .map(line -> line == null
                                ? null
                                : new RecordTransactionCommand.Line(line.sku(), line.quantity()))
                        .toList();

        return new RecordTransactionCommand(request.tillId(), request.occurredAt(), lines);
    }

    private TransactionResponseDto toResponse(TransactionView view) {
        List<TransactionResponseDto.LineItemDto> lines = view.lineItems().stream()
                .map(line -> new TransactionResponseDto.LineItemDto(
                        line.sku(),
                        line.quantity(),
                        new MoneyDto(line.unitPrice(), line.currency()),
                        line.markdownApplied()))
                .toList();

        return new TransactionResponseDto(
                view.transactionId(),
                view.tillId(),
                view.occurredAt(),
                new MoneyDto(view.totalAmount(), view.currency()),
                lines);
    }
}
