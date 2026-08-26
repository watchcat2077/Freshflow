package com.freshflow.inventory.domain;

import java.time.Instant;

/**
 * Domain event (spec-01 &sect;2.4) &mdash; a batch was received.
 *
 * <p>This is the <i>internal</i> form of the event, expressed in this context's own value objects.
 * spec-01 &sect;2.6 distinguishes internal events from external integration events; the
 * infrastructure adapter translates this one into the {@code batch-received} integration event
 * defined in the {@code contracts} module. Keeping the two apart is what lets the domain depend on
 * nothing at all while the Published Language still governs everything that crosses a context
 * boundary.
 *
 * <p>{@code occurredAt} is producer-assigned event time (spec-01 &sect;2.6). {@code eventId} is not
 * carried here: it identifies a publication rather than a fact, so the publishing adapter assigns
 * it.
 */
public record BatchReceived(BatchId batchId, Sku sku, Quantity quantity, ExpiryDate expiryAt,
                            Money unitCost, Instant occurredAt) {
}
