package com.freshflow.inventory.application;

import com.freshflow.inventory.domain.BatchId;
import com.freshflow.inventory.domain.DomainEventPublisher;
import com.freshflow.inventory.domain.DomainRuleViolation;
import com.freshflow.inventory.domain.ExpiryDate;
import com.freshflow.inventory.domain.Money;
import com.freshflow.inventory.domain.Quantity;
import com.freshflow.inventory.domain.Sku;
import com.freshflow.inventory.domain.StockBatch;
import com.freshflow.inventory.infrastructure.repository.StockBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Application service (spec-01 &sect;2.1) &mdash; the use cases of the Inventory &amp; Batch context.
 *
 * <p>Depends inward on {@code domain} only. It orchestrates: it retrieves domain objects, delegates
 * the decisions to them, and saves the result. It decides no business rule of its own &mdash; every
 * rule below either belongs to {@link StockBatch} or is request-shape validation, which spec-01
 * &sect;1.1 places here pending the M-9 coordinator ruling on
 * {@code spring-boot-starter-validation}.
 *
 * <p>The distinction matters and is deliberate:
 * <ul>
 *   <li><b>Shape</b> &mdash; "quantity is missing", "unitCost has no currency". Hand-rolled here.</li>
 *   <li><b>Business rule</b> &mdash; "quantity must be positive", "expiry must be in the future".
 *       Enforced inside the aggregate; this layer only transports the violation outward.</li>
 * </ul>
 */
@Service
public class BatchApplicationService {

    private final StockBatchRepository stockBatchRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public BatchApplicationService(StockBatchRepository stockBatchRepository,
                                   DomainEventPublisher domainEventPublisher,
                                   Clock clock) {
        this.stockBatchRepository = stockBatchRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * FR1 &mdash; register a goods receipt as a stock batch, and announce it.
     */
    @Transactional
    public ApplicationResult<BatchView> registerGoodsReceipt(RegisterGoodsReceiptCommand command) {
        String shapeError = validateShape(command);
        if (shapeError != null) {
            return ApplicationResult.failure("VALIDATION_FAILED", shapeError);
        }

        Instant now = clock.instant();
        try {
            StockBatch batch = StockBatch.receive(
                    BatchId.newId(),
                    Sku.of(command.sku()),
                    Quantity.of(command.quantity()),
                    ExpiryDate.of(command.expiryAt()),
                    Money.of(command.unitCostAmount(), command.unitCostCurrency()),
                    now);

            stockBatchRepository.save(batch);
            // FR1: "Publishes batch-received". Week 5 binds a logging adapter; see spec-01 s2.6.
            domainEventPublisher.publish(batch.toBatchReceivedEvent());

            return ApplicationResult.success(toView(batch, now));
        } catch (DomainRuleViolation violation) {
            // The domain decided this, not us. We only carry the verdict to the edge.
            return ApplicationResult.failure(violation.code(), violation.getMessage());
        }
    }

    /**
     * FR4 &mdash; query a batch. An empty result is not a failure of the request, so it is modelled
     * as an absent value rather than as an error; the controller maps it to 404.
     */
    @Transactional(readOnly = true)
    public Optional<BatchView> findBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return stockBatchRepository.findById(BatchId.of(batchId))
                .map(batch -> toView(batch, now));
    }

    /**
     * Request-shape validation only. Every rule here is about whether the request is well formed,
     * never about whether it is allowed. Returns null when the shape is acceptable.
     */
    private String validateShape(RegisterGoodsReceiptCommand command) {
        if (command == null) {
            return "Request body is required.";
        }
        if (command.sku() == null || command.sku().isBlank()) {
            return "Field 'sku' is required.";
        }
        if (command.quantity() == null) {
            return "Field 'quantity' is required.";
        }
        if (command.expiryAt() == null) {
            return "Field 'expiryAt' is required and must be an ISO-8601 timestamp.";
        }
        if (command.unitCostAmount() == null) {
            return "Field 'unitCost.amount' is required.";
        }
        if (command.unitCostCurrency() == null || command.unitCostCurrency().isBlank()) {
            return "Field 'unitCost.currency' is required.";
        }
        return null;
    }

    private BatchView toView(StockBatch batch, Instant now) {
        return new BatchView(
                batch.batchId().value(),
                batch.sku().value(),
                batch.receivedQuantity().value(),
                batch.remainingQuantity().value(),
                batch.expiryAt().value(),
                batch.unitCost().amount(),
                batch.unitCost().currency(),
                batch.status().name(),
                batch.remainingShelfLife(now).truncatedTo(ChronoUnit.HOURS).toHours());
    }
}
