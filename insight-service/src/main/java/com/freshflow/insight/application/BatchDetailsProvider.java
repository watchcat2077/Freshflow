package com.freshflow.insight.application;

import java.util.Optional;

/**
 * Outbound port &mdash; batch facts for the {@code getBatchDetails} tool (FR14).
 *
 * <p>Declared in {@code application} per spec-01 &sect;2.3's port-placement table and ADR 0002
 * Decision 2, which keep this service's ports in {@code application}; the {@code domain} package
 * of ADR 0006 declares no port. The adapter behind it, {@code RestBatchDetailsProvider}, lives in
 * {@code infrastructure}.
 *
 * <p><b>Why a port.</b> {@code batch-received} carries {@code unitCost}, but the FR13 batch-state
 * table drops it (spec-05 &sect;3.1), so unit cost exists nowhere in this service's own state.
 * spec-06 &sect;3 sets out three ways of obtaining it and settles on a synchronous
 * {@code GET /api/batches/&#123;batchId&#125;} (team ruling R6). The return shape is fixed here
 * and is the same whatever the source &mdash; batchId, sku, remaining quantity, expiry and unit
 * cost &mdash; and every test runs against a stub of this interface, so the source is a one-class
 * decision. That is the argument ADR 0001 made for {@code PriceSnapshotFeed} and ADR 0003 made for
 * the velocity baseline, used a third time.
 *
 * <p>An empty result means the batch is unknown to whatever source is bound. That is not an error
 * and must not be turned into one &mdash; see spec-06 &sect;4 for what the tool reports in that
 * case.
 */
public interface BatchDetailsProvider {

    Optional<BatchDetails> findBatch(String batchId);
}
