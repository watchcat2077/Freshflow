package com.freshflow.sales.domain;

import java.time.Instant;

/**
 * spec-03 &sect;2 &mdash; the local read cache of price data owned by {@code sales-service}.
 *
 * <p>{@code sales-service} never queries {@code pricing-service} synchronously (spec-01 &sect;1.3).
 * This snapshot is populated through the {@code PriceSnapshotFeed} inbound port, which ADR 0001
 * places in the application layer.
 *
 * <p>In Week 5 only one case exists: no markdown is in force, so {@code effectivePrice} equals
 * {@code basePrice}, {@code discountTier} is null and the window is empty. The markdown-carrying
 * fields are present because spec-03 &sect;2 defines them and FR6b will populate them from Week 6;
 * nothing reads them yet.
 */
public record PriceSnapshot(Sku sku,
                            Money basePrice,
                            Money effectivePrice,
                            Money unitCost,
                            String category,
                            String discountTier,
                            Instant effectiveFrom,
                            Instant effectiveTo) {

    /** The Week 5 shape: base price only, no markdown (spec-03 &sect;2). */
    public static PriceSnapshot withoutMarkdown(Sku sku, Money basePrice, Money unitCost, String category) {
        return new PriceSnapshot(sku, basePrice, basePrice, unitCost, category, null, null, null);
    }
}
