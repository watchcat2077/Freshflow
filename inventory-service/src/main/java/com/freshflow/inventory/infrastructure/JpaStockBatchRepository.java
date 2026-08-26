package com.freshflow.inventory.infrastructure;

import com.freshflow.inventory.domain.BatchId;
import com.freshflow.inventory.domain.BatchStatus;
import com.freshflow.inventory.domain.Sku;
import com.freshflow.inventory.domain.StockBatch;
import com.freshflow.inventory.domain.StockBatchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adapter implementing the domain's {@code StockBatchRepository} (spec-01 &sect;2.2: "Repository
 * interfaces are declared in domain. Their JPA implementations live in infrastructure").
 *
 * <p>This is the dependency inversion made concrete. The application layer is wired to the domain
 * interface and has no idea JPA is underneath.
 */
@Repository
public class JpaStockBatchRepository implements StockBatchRepository {

    private final StockBatchJpaRepository jpaRepository;

    public JpaStockBatchRepository(StockBatchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(StockBatch batch) {
        jpaRepository.save(StockBatchMapper.toEntity(batch));
    }

    @Override
    public Optional<StockBatch> findById(BatchId batchId) {
        return jpaRepository.findById(batchId.value()).map(StockBatchMapper::toDomain);
    }

    @Override
    public List<StockBatch> findActiveBySkuOrderByExpiryAsc(Sku sku) {
        return jpaRepository
                .findBySkuAndStatusOrderByExpiryAtAsc(sku.value(), BatchStatus.ACTIVE.name())
                .stream()
                .map(StockBatchMapper::toDomain)
                .toList();
    }
}
