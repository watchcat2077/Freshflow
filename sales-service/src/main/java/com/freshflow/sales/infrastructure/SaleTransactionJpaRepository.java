package com.freshflow.sales.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@link SaleTransactionJpaEntity}. An infrastructure detail. */
public interface SaleTransactionJpaRepository extends JpaRepository<SaleTransactionJpaEntity, String> {
}
