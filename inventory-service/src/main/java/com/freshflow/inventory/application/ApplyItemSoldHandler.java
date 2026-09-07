package com.freshflow.inventory.application;

import com.freshflow.inventory.domain.DeductionPlan;
import com.freshflow.inventory.domain.DomainEventPublisher;
import com.freshflow.inventory.domain.FefoDeductionPolicy;
import com.freshflow.inventory.domain.Quantity;
import com.freshflow.inventory.domain.Sku;
import com.freshflow.inventory.domain.StockBatch;
import com.freshflow.inventory.domain.StockBatchRepository;
import com.freshflow.inventory.domain.StockLevelChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FR2 &mdash; deduct stock on sale. One command, one handler, per CLAUDE.md's preference for a
 * handler per complex use case over a bloated application service.
 *
 * <p>It orchestrates and decides nothing. It loads the SKU's active batches in expiry order, asks
 * {@link FefoDeductionPolicy} which of them supply the sale, tells each {@code StockBatch} to
 * deduct its share, saves them, and announces what changed. Every rule it appears to apply belongs
 * to something else:
 *
 * <ul>
 *   <li><b>Which batches, in what order</b> &mdash; {@link FefoDeductionPolicy}, a domain service,
 *       because the rule spans aggregates (spec-03 &sect;1).</li>
 *   <li><b>Stock must never go negative</b>, and no deduction from an expired or written-off batch
 *       &mdash; inside {@code StockBatch}, the aggregate that owns the number (spec-01
 *       &sect;2.5).</li>
 *   <li><b>Not applying the same message twice</b> &mdash; the inbound adapter. See
 *       {@code ApplyItemSoldCommand}.</li>
 * </ul>
 */
@Service
public class ApplyItemSoldHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplyItemSoldHandler.class);

    private final StockBatchRepository stockBatchRepository;
    private final FefoDeductionPolicy fefoDeductionPolicy;
    private final DomainEventPublisher domainEventPublisher;

    public ApplyItemSoldHandler(StockBatchRepository stockBatchRepository,
                               FefoDeductionPolicy fefoDeductionPolicy,
                               DomainEventPublisher domainEventPublisher) {
        this.stockBatchRepository = stockBatchRepository;
        this.fefoDeductionPolicy = fefoDeductionPolicy;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Applies one sale. Publishes one {@code stock-level-changed} per batch actually touched.
     *
     * @return how many units could not be drawn from any batch; zero in the ordinary case
     */
    @Transactional
    public int handle(ApplyItemSoldCommand command) {
        if (command == null || command.sku() == null || command.sku().isBlank()
                || command.quantity() == null || command.quantity() < 1) {
            log.warn("Ignoring a malformed item-sold command: {}", command);
            return 0;
        }

        Sku sku = Sku.of(command.sku());
        Quantity sold = Quantity.of(command.quantity());

        // FEFO ordering is the repository's job to retrieve and the policy's to apply.
        List<StockBatch> candidates = stockBatchRepository.findActiveBySkuOrderByExpiryAsc(sku);
        DeductionPlan plan = fefoDeductionPolicy.planDeduction(candidates, sold, command.occurredAt());

        for (DeductionPlan.Allocation allocation : plan.allocations()) {
            StockBatch batch = allocation.batch();

            // The aggregate enforces "never negative" and refuses expired or written-off batches.
            // The policy planned this allocation; the batch still gets to reject it.
            batch.deduct(allocation.quantity(), command.occurredAt());
            stockBatchRepository.save(batch);

            domainEventPublisher.publish(new StockLevelChanged(
                    batch.batchId(), batch.sku(), batch.remainingQuantity(), command.occurredAt()));
        }

        if (!plan.isFullyFulfilled()) {
            // Not an exception. The sale already happened upstream, and refusing to record the
            // part that stock could cover would leave remaining quantity overstated for good --
            // the opposite of what FR2 exists to achieve. It is reported loudly instead, because
            // it means this context's stock figures disagreed with what the shop actually sold.
            log.warn("FR2: sale of {} x {} exceeded available stock by {} unit(s). Deducted what "
                            + "was on hand across {} batch(es); stock never went negative.",
                    sold.value(), sku.value(), plan.unfulfilled().value(), plan.allocations().size());
        }

        return plan.unfulfilled().value();
    }
}
