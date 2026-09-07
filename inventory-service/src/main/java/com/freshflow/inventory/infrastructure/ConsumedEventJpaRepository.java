package com.freshflow.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@link ConsumedEventJpaEntity}. An infrastructure detail, and
 * deliberately not exposed through any port: no domain or application type refers to a consumed
 * event id.
 */
public interface ConsumedEventJpaRepository extends JpaRepository<ConsumedEventJpaEntity, String> {
}
