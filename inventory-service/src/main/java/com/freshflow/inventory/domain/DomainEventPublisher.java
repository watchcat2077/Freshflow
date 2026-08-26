package com.freshflow.inventory.domain;

/**
 * Outbound port (spec-01 &sect;2.2) &mdash; how this context announces something that happened.
 *
 * <p>Declared in {@code domain} and implemented in {@code infrastructure}. That is the dependency
 * inversion the layering requires: the inner layer states what it needs, the outer layer adapts to
 * it.
 *
 * <p>Week 5 binds a logging adapter, because no broker runs. Week 6 replaces it with a Kafka
 * adapter behind this unchanged interface, and nothing in {@code domain} or {@code application}
 * changes when it does.
 */
public interface DomainEventPublisher {

    void publish(BatchReceived event);
}
