package com.freshflow.insight.application;

import java.util.Optional;

/**
 * Outbound port &mdash; batch facts for the {@code getBatchDetails} tool (FR14).
 *
 * <p>Declared in {@code application} per spec-01 &sect;2.3's port-placement table and ADR 0002
 * Decision 2, which assign this service's ports to {@code application} because it has no
 * {@code domain} package to declare them in. The adapter behind it lives in
 * {@code infrastructure}, as every adapter does.
 *
 * <p><b>This port exists to make an unsettled question deferrable.</b> {@code batch-received}
 * carries {@code unitCost}, but the FR13 batch-state table drops it, so unit cost does not
 * currently exist anywhere in this service. Three ways of getting it are set out in spec-06
 * &sect;3 and the choice is the team's. The port is written so that the choice changes one class:
 *
 * <ul>
 *   <li>The return shape is fixed here and is the same whatever the source &mdash; batchId, sku,
 *       remaining quantity, expiry and unit cost.</li>
 *   <li>Every test runs against a stub of this interface, so no test asserts anything about the
 *       source and no test changes when the ruling lands.</li>
 *   <li>The ruling therefore decides only which adapter is written in Phase B.</li>
 * </ul>
 *
 * <p>That is the argument ADR 0001 made for {@code PriceSnapshotFeed} and ADR 0003 made for the
 * velocity baseline, used a third time. It is worth noticing that this is the third time: a port
 * at every boundary whose implementation is genuinely undecided is turning out to be the thing
 * that lets this project settle questions late without rewriting what depends on them.
 *
 * <p>An empty result means the batch is unknown to whatever source is bound. That is not an error
 * and must not be turned into one &mdash; see spec-06 &sect;4 for what the tool reports in that
 * case.
 */
public interface BatchDetailsProvider {

    Optional<BatchDetails> findBatch(String batchId);
}
