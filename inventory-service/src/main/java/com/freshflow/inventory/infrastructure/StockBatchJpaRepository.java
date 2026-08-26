package com.freshflow.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository over {@link StockBatchJpaEntity}.
 *
 * <p>An infrastructure detail, not the domain's repository. The domain's is
 * {@code StockBatchRepository}, declared in {@code domain}; {@link JpaStockBatchRepository} adapts
 * this to that. Keeping them separate is what stops Spring Data's type from leaking inward.
 */
public interface StockBatchJpaRepository extends JpaRepository<StockBatchJpaEntity, String> {

    List<StockBatchJpaEntity> findBySkuAndStatusOrderByExpiryAtAsc(String sku, String status);
}
