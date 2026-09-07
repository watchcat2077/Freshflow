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
 *
 * <p>Week 7 adds a second method for FR2's {@code stock-level-changed}. Note what that is and is
 * not: the port gained a capability the context did not previously have, which is ordinary growth.
 * Neither existing method changed shape, so no existing caller or adapter was disturbed.
 */
public interface DomainEventPublisher {

    void publish(BatchReceived event);

    /** FR2 &mdash; what is left of a batch changed. One per batch actually touched by a sale. */
    void publish(StockLevelChanged event);
}
