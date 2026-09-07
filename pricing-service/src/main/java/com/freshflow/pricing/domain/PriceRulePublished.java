package com.freshflow.pricing.domain;

import java.time.Instant;

/**
 * Domain event (spec-01 &sect;2.4) &mdash; the price rule in force for a SKU was published.
 *
 * <p>This is the <i>internal</i> form, expressed in this context's own value objects. spec-01
 * &sect;2.6 distinguishes internal events from external integration events; the infrastructure
 * adapter translates this one into the {@code price-rule-published} integration event defined in
 * the {@code contracts} module. Keeping the two apart is what lets {@code domain} import nothing
 * at all while the Published Language still governs everything crossing a context boundary.
 *
 * <p>{@code occurredAt} is producer-assigned event time (spec-01 &sect;2.6), and it is what the
 * consuming side's last-write-wins rule compares. {@code eventId} is not carried here: it
 * identifies a publication rather than a fact, so the publishing adapter mints it.
 */
public record PriceRulePublished(Sku sku,
                                 Money basePrice,
                                 Money unitCost,
                                 String category,
                                 Instant occurredAt) {
}
