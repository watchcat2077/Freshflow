package com.freshflow.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate Root (spec-01 &sect;2.4, spec-03 &sect;1) &mdash; a physical delivery of one SKU with its
 * own expiry and cost.
 *
 * <p>A Plain Old Java Object, persistence ignorant, carrying no JPA annotation and importing
 * nothing outside {@code java.*} (spec-01 &sect;&sect;2.2 and 2.3). Its JPA counterpart and the
 * mapper between them live in {@code infrastructure}.
 *
 * <p><b>Invariants enforced here and nowhere else</b> (spec-01 &sect;2.5):
 * <ol>
 *   <li>{@code remainingQuantity} never becomes negative.</li>
 *   <li>{@code deduct} is rejected on a WRITTEN_OFF or expired batch.</li>
 *   <li>{@code receivedQuantity} and {@code unitCost} are immutable after creation.</li>
 *   <li>{@code expiryAt} must be after the receipt timestamp.</li>
 *   <li>Transition to DEPLETED happens automatically when {@code remainingQuantity} reaches zero.</li>
 * </ol>
 */
public class StockBatch {

    private final BatchId batchId;
    private final Sku sku;
    private final Quantity receivedQuantity;
    private final ExpiryDate expiryAt;
    private final Money unitCost;
    private final Instant receivedAt;

    private Quantity remainingQuantity;
    private BatchStatus status;

    private StockBatch(BatchId batchId, Sku sku, Quantity receivedQuantity, Quantity remainingQuantity,
                       ExpiryDate expiryAt, Money unitCost, BatchStatus status, Instant receivedAt) {
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.receivedQuantity = Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        this.remainingQuantity = Objects.requireNonNull(remainingQuantity, "remainingQuantity");
        this.expiryAt = Objects.requireNonNull(expiryAt, "expiryAt");
        this.unitCost = Objects.requireNonNull(unitCost, "unitCost");
        this.status = Objects.requireNonNull(status, "status");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * Registers a goods receipt as a new batch (FR1).
     *
     * <p>This is the only way a {@code StockBatch} comes into existence, which is what makes
     * invariants 3 and 4 unavoidable rather than merely documented.
     */
    public static StockBatch receive(BatchId batchId, Sku sku, Quantity quantity, ExpiryDate expiryAt,
                                     Money unitCost, Instant receivedAt) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(expiryAt, "expiryAt");
        Objects.requireNonNull(receivedAt, "receivedAt");

        // FR1: "Quantity must be positive". Quantity itself permits zero because a depleted batch
        // has zero remaining; a receipt of zero is a different question, and it is asked here.
        if (!quantity.isPositive()) {
            throw new DomainRuleViolation("QUANTITY_NOT_POSITIVE",
                    "Received quantity must be greater than zero.");
        }
        // Invariant 4, and FR1's "expiry must be in the future".
        if (!expiryAt.isAfter(receivedAt)) {
            throw new DomainRuleViolation("EXPIRY_NOT_IN_FUTURE",
                    "Expiry must be after the receipt timestamp.");
        }
        return new StockBatch(batchId, sku, quantity, quantity, expiryAt, unitCost,
                BatchStatus.ACTIVE, receivedAt);
    }

    /**
     * Rebuilds a batch from storage. Deliberately bypasses {@link #receive}: a persisted batch was
     * already valid when it was created, and re-running the receipt rules against a later "now"
     * would reject every batch that has since expired.
     */
    public static StockBatch rehydrate(BatchId batchId, Sku sku, Quantity receivedQuantity,
                                       Quantity remainingQuantity, ExpiryDate expiryAt, Money unitCost,
                                       BatchStatus status, Instant receivedAt) {
        if (remainingQuantity.value() > receivedQuantity.value()) {
            throw new DomainRuleViolation("REMAINING_EXCEEDS_RECEIVED",
                    "Remaining quantity cannot exceed received quantity.");
        }
        return new StockBatch(batchId, sku, receivedQuantity, remainingQuantity, expiryAt, unitCost,
                status, receivedAt);
    }

    /**
     * Deducts sold units (FR2). The rule is stated here because it is an invariant of this
     * aggregate; {@code ApplyItemSoldHandler} calls it for each batch {@code FefoDeductionPolicy}
     * allocates a sale to.
     */
    public void deduct(Quantity quantity, Instant now) {
        if (status == BatchStatus.WRITTEN_OFF) {
            throw new DomainRuleViolation("BATCH_WRITTEN_OFF",
                    "A written-off batch cannot be deducted from.");
        }
        if (isExpired(now)) {
            throw new DomainRuleViolation("BATCH_EXPIRED",
                    "An expired batch cannot be deducted from.");
        }
        if (remainingQuantity.isLessThan(quantity)) {
            throw new DomainRuleViolation("INSUFFICIENT_STOCK",
                    "Stock on hand must never become negative.");
        }
        remainingQuantity = remainingQuantity.minus(quantity);
        if (remainingQuantity.isZero()) {
            status = BatchStatus.DEPLETED;
        }
    }

    public boolean isExpired(Instant now) {
        return expiryAt.hasPassedBy(now);
    }

    public Duration remainingShelfLife(Instant now) {
        return expiryAt.remainingShelfLifeAt(now);
    }

    /**
     * The domain event emitted on receipt, published through {@link DomainEventPublisher}.
     */
    public BatchReceived toBatchReceivedEvent() {
        return new BatchReceived(batchId, sku, receivedQuantity, expiryAt, unitCost, receivedAt);
    }

    public BatchId batchId() {
        return batchId;
    }

    public Sku sku() {
        return sku;
    }

    public Quantity receivedQuantity() {
        return receivedQuantity;
    }

    public Quantity remainingQuantity() {
        return remainingQuantity;
    }

    public ExpiryDate expiryAt() {
        return expiryAt;
    }

    public Money unitCost() {
        return unitCost;
    }

    public BatchStatus status() {
        return status;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
