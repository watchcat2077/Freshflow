package com.freshflow.sales.application;

import com.freshflow.sales.domain.PriceSnapshot;
import com.freshflow.sales.domain.Sku;

import java.util.Optional;

/**
 * Inbound port (spec-01 &sect;1.3) &mdash; the ONLY channel by which price data enters
 * {@code sales-service}.
 *
 * <p>Declared in the application layer, not the domain layer, per ADR 0001: spec-01 &sect;2.3
 * forbids repository abstractions in this service's domain, and a port over an external data
 * source is near enough to one that declaring it there would breach that ruling in substance.
 *
 * <p>The bound adapter is {@code PriceRulePublishedSnapshotFeed} in {@code infrastructure}, which
 * materialises the snapshot from {@code price-rule-published} and {@code markdown-applied} (FR6a,
 * FR6b); any adapter may stand behind this signature. Introducing a synchronous
 * {@code pricing-service} client instead is a specification violation (spec-01 &sect;1.3).
 */
public interface PriceSnapshotFeed {

    Optional<PriceSnapshot> findBySku(Sku sku);
}
