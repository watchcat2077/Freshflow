package com.freshflow.insight.application;

import com.freshflow.contracts.MoneyPayload;

import java.time.Instant;

/**
 * What the {@code getBatchDetails} tool returns (spec-02 FR14: "remaining quantity, expiry
 * timestamp, unit cost"), plus the two identifiers needed to make sense of them.
 *
 * <p>This record is the reason the unit-cost question can be deferred. Where the numbers come from
 * is an open ruling &mdash; spec-06 &sect;3 sets out the options &mdash; and nothing in this record,
 * in {@link BatchDetailsProvider}, or in any test that uses them depends on the answer. The ruling
 * decides which adapter is written, and only that.
 *
 * <p>{@code unitCost} is {@code MoneyPayload} from the {@code contracts} module rather than a local
 * money type. The Insight context is a Conformist (spec-01 &sect;1.5): it accepts upstream models
 * without negotiation, and inventing an Insight-flavoured {@code Money} to hold a number that
 * arrives on {@code batch-received} would be negotiating.
 *
 * <p><b>Unit cost here is evidence, not enforcement.</b> The agent is given the cost so that its
 * written rationale can be about a real commercial situation rather than about a percentage in the
 * abstract. It does not follow, and must not be made to follow, that this service checks anything
 * against it: spec-01 &sect;2.5 and &sect;1.5 place that check in the Pricing context's domain
 * layer, and spec-06 &sect;7 states the rule mechanically.
 */
public record BatchDetails(String batchId,
                           String sku,
                           int remainingQuantity,
                           Instant expiryAt,
                           MoneyPayload unitCost) {
}
