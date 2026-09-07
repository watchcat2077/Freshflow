package com.freshflow.inventory.application;

import com.freshflow.inventory.domain.DomainEventPublisher;
import com.freshflow.inventory.domain.FefoDeductionPolicy;
import com.freshflow.inventory.domain.StockBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        throw new UnsupportedOperationException("FR2 is not implemented yet.");
    }
}
