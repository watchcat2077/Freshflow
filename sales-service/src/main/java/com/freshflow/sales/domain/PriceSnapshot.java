package com.freshflow.sales.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * spec-03 &sect;2 &mdash; the local read cache of price data owned by {@code sales-service}.
 *
 * <p>{@code sales-service} never queries {@code pricing-service} synchronously (spec-01 &sect;1.3).
 * This snapshot is populated through the {@code PriceSnapshotFeed} inbound port, which ADR 0001
 * places in the application layer.
 *
 * <p>Two shapes exist. <b>Without a markdown</b> &mdash; the only shape in Week 5, and still the
 * shape {@code price-rule-published} alone produces &mdash; {@code effectivePrice} equals
 * {@code basePrice}, {@code discountTier} is null and the window is empty. <b>With a markdown</b>
 * &mdash; FR6b, fed from {@code markdown-applied} since 4 October 2026 &mdash; {@code effectivePrice}
 * is the discounted price the Pricing context authorised, {@code discountTier} names the tier and
 * {@code effectiveFrom}/{@code effectiveTo} bound the promotion window. The discounted price is
 * carried, never recomputed from the tier: what a tier does to a price is Pricing's rule, and
 * re-deriving it here would duplicate that rule outside its context.
 *
 * <p>{@link #priceAt} answers which of the two prices is in force at a given instant. It is a
 * value query over the snapshot's own fields, in the same sense that {@code Money#multiply} is
 * value arithmetic rather than a business rule. The business decision &mdash; that a line is priced
 * at the transaction's {@code occurredAt} rather than at processing time, and that a SKU absent
 * from the snapshot is a 400 &mdash; stays in the transaction script (spec-01 &sect;2.5, ADR 0002),
 * which chooses the instant to ask about and acts on the answer. This package still declares no
 * interface, no aggregate and no repository.
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

    /**
     * The same base data with a markdown attached (FR6b). {@code discountedPrice} is the amount
     * {@code markdown-applied} carried, which becomes {@code effectivePrice}; the base price,
     * unit cost and category are untouched.
     */
    public PriceSnapshot withMarkdown(Money discountedPrice, String discountTier,
                                      Instant effectiveFrom, Instant effectiveTo) {
        Objects.requireNonNull(discountedPrice, "discountedPrice");
        Objects.requireNonNull(discountTier, "discountTier");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(effectiveTo, "effectiveTo");
        return new PriceSnapshot(sku, basePrice, discountedPrice, unitCost, category,
                discountTier, effectiveFrom, effectiveTo);
    }

    /** True when a markdown, in force or not, is attached to this snapshot. */
    public boolean hasMarkdown() {
        return discountTier != null && effectiveFrom != null && effectiveTo != null;
    }

    /**
     * The price in force at {@code instant} &mdash; spec-03 &sect;2's rule, "the price recorded on
     * a LineItem is the price in force at the transaction's occurredAt, not the price at the time
     * of processing", answered for one instant.
     *
     * <p>A markdown is in force only inside its promotion window (FR6b). The window is
     * <b>half-open</b>, {@code [effectiveFrom, effectiveTo)}: the markdown applies from
     * {@code effectiveFrom} inclusive and ends at {@code effectiveTo}, so a scan stamped at exactly
     * {@code effectiveTo} already records the base price. FR10 describes a markdown as ending "at
     * the close of its promotion window", and a window that still applied at its own closing
     * instant would never quite close. Outside the window &mdash; before it opens, or after it
     * closes with no further event having arrived &mdash; the base price is in force and
     * {@code markdownApplied} is false.
     */
    public PriceInForce priceAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (isMarkdownInForceAt(instant)) {
            return new PriceInForce(effectivePrice, true);
        }
        return new PriceInForce(basePrice, false);
    }

    private boolean isMarkdownInForceAt(Instant instant) {
        return hasMarkdown() && !instant.isBefore(effectiveFrom) && instant.isBefore(effectiveTo);
    }

    /**
     * What {@link #priceAt} answers: the unit price to record on the line, and whether a markdown
     * produced it &mdash; the two values spec-03 &sect;2's {@code LineItem} carries.
     */
    public record PriceInForce(Money unitPrice, boolean markdownApplied) {
    }
}
