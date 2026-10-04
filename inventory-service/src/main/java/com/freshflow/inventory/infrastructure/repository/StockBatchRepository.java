package com.freshflow.inventory.infrastructure.repository;

import com.freshflow.inventory.domain.BatchId;
import com.freshflow.inventory.domain.Sku;
import com.freshflow.inventory.domain.StockBatch;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface, declared in {@code domain} per spec-01 &sect;2.2 and spec-03 &sect;1. The JPA
 * implementation lives in {@code infrastructure}.
 *
 * <p>The signature speaks only the domain's language &mdash; {@link BatchId}, {@link Sku},
 * {@link StockBatch} &mdash; and says nothing about how or where anything is stored.
 */
public interface StockBatchRepository {

    void save(StockBatch batch);

    Optional<StockBatch> findById(BatchId batchId);

    /**
     * FEFO ordering, for {@code FefoDeductionPolicy} (FR2) and, since 4 October 2026, for FR4's
     * {@code GET /api/batches?sku=}, which spec-02 requires to list batches "in the same order FR2
     * deducts them in" &mdash; one query, so the two cannot disagree.
     */
    List<StockBatch> findActiveBySkuOrderByExpiryAsc(Sku sku);
}
