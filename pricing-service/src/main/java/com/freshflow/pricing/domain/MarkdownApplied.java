package com.freshflow.pricing.domain;

import java.time.Instant;

/**
 * Domain event (spec-01 &sect;2.4) &mdash; a markdown plan was activated and its price is now in
 * force for its window.
 *
 * <p>The <i>internal</i> form, in this context's own value objects; the infrastructure adapter
 * translates it into the {@code markdown-applied} integration event of the {@code contracts}
 * module (spec-01 &sect;2.6, Table A), which {@code sales-service} consumes for FR6b and
 * {@code insight-service} may read as evidence. Keeping the two apart is what lets {@code domain}
 * import nothing while the Published Language still governs what crosses the boundary.
 *
 * <p>{@code discountedPrice} travels on the event rather than being recomputed by consumers, so
 * the price a till charges is the one this context authorised, to the cent. {@code occurredAt} is
 * producer-assigned event time (spec-01 &sect;2.6); {@code eventId} is not carried here because it
 * identifies a publication rather than a fact, and the publishing adapter mints it.
 */
public record MarkdownApplied(MarkdownPlanId markdownPlanId,
                              BatchId batchId,
                              Sku sku,
                              DiscountTier discountTier,
                              PromotionWindow window,
                              Money discountedPrice,
                              Instant occurredAt) {
}
