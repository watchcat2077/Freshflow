package com.freshflow.inventory.infrastructure;

import com.freshflow.inventory.domain.BatchId;
import com.freshflow.inventory.domain.BatchStatus;
import com.freshflow.inventory.domain.ExpiryDate;
import com.freshflow.inventory.domain.Money;
import com.freshflow.inventory.domain.Quantity;
import com.freshflow.inventory.domain.Sku;
import com.freshflow.inventory.domain.StockBatch;

/**
 * Translates between the persistence-ignorant aggregate and its JPA entity (spec-01 &sect;2.3).
 *
 * <p>The direction of dependency is the point: this class, in {@code infrastructure}, knows about
 * {@code domain}. Nothing in {@code domain} knows this class exists.
 */
final class StockBatchMapper {

    private StockBatchMapper() {
    }

    static StockBatchJpaEntity toEntity(StockBatch batch) {
        return new StockBatchJpaEntity(
                batch.batchId().value(),
                batch.sku().value(),
                batch.receivedQuantity().value(),
                batch.remainingQuantity().value(),
                batch.expiryAt().value(),
                batch.unitCost().amount(),
                batch.unitCost().currency(),
                batch.status().name(),
                batch.receivedAt());
    }

    static StockBatch toDomain(StockBatchJpaEntity entity) {
        return StockBatch.rehydrate(
                BatchId.of(entity.getBatchId()),
                Sku.of(entity.getSku()),
                Quantity.of(entity.getReceivedQuantity()),
                Quantity.of(entity.getRemainingQuantity()),
                ExpiryDate.of(entity.getExpiryAt()),
                Money.of(entity.getUnitCostAmount(), entity.getUnitCostCurrency()),
                BatchStatus.valueOf(entity.getStatus()),
                entity.getReceivedAt());
    }
}
