package com.freshflow.inventory.domain;

/**
 * Outbound port (spec-01 &sect;2.2) &mdash; how this context announces something that happened.
 *
 * <p>Declared in {@code domain} and implemented in {@code infrastructure}. That is the dependency
 * inversion the layering requires: the inner layer states what it needs, the outer layer adapts to
 * it.
 *
 * <p>The adapter behind it is {@code KafkaDomainEventPublisher} in {@code infrastructure}, which
 * translates each internal event into the {@code contracts} module's record and publishes it.
 * The adapter can be replaced without anything in {@code domain} or {@code application} changing.
 *
 * <p>One method per event this context announces: {@code batch-received} (FR1) and
 * {@code stock-level-changed} (FR2).
 */
public interface DomainEventPublisher {

    void publish(BatchReceived event);

    /** FR2 &mdash; what is left of a batch changed. One per batch actually touched by a sale. */
    void publish(StockLevelChanged event);
}
