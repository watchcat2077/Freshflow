package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event on the {@code markdown-applied} topic (spec-01 s2.6, Table A).
 *
 * <p>Produced by {@code pricing-service} when a markdown plan is activated (FR9), which can only
 * happen after the margin floor has been checked inside the Pricing domain layer (FR11). Consumed
 * by {@code sales-service}, which uses it to apply the live markdown at the point of scan (FR6b),
 * and available to {@code insight-service} as read-side evidence of what was actually marked down.
 *
 * <p>{@code discountedPrice} is carried on the event rather than recomputed by consumers so that
 * the price a till charges is the one the Pricing context authorised, to the cent. Recomputing it
 * elsewhere would duplicate a Pricing rule outside the Pricing context.
 *
 * <p>Like every event in this module it carries {@code eventId} for idempotent consumption and a
 * producer-assigned {@code occurredAt}; consumers MUST deduplicate on {@code eventId}.
 */
public record MarkdownAppliedEvent(UUID eventId,
                                   Instant occurredAt,
                                   String markdownPlanId,
                                   String batchId,
                                   String sku,
                                   DiscountTier discountTier,
                                   Instant effectiveFrom,
                                   Instant effectiveTo,
                                   MoneyPayload discountedPrice) implements IntegrationEvent {

    public static final String TOPIC = "markdown-applied";
}
