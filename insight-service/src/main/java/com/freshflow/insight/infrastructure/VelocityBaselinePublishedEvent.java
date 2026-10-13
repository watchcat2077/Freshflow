package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * The payload of the {@code velocity-baseline-published} topic &mdash; a SKU's normal selling rate,
 * in units per hour.
 *
 * <p>FR12 requires the baseline to be "held in a {@code KTable}", and a KTable is built from a
 * topic; this is that topic's record. The values are seeded from configuration by
 * {@link VelocityBaselineSeeder} (spec-01 &sect;2.7): spec-02 FR12 requires a baseline and no
 * requirement says where it comes from, a gap spec-01 &sect;2.7 records.
 *
 * <p><b>Why this is not in the {@code contracts} module.</b> That module is the Published Language
 * of spec-01 &sect;1.5 &mdash; the shared vocabulary between bounded contexts. This topic never
 * leaves the Insight context: insight publishes it and insight alone consumes it. Putting it in
 * {@code contracts} would advertise it as a cross-context contract and invite another context to
 * depend on a number that is purely an implementation detail of this one. It is an internal event
 * that happens to travel through Kafka, which is the case spec-01 s2.6's internal-versus-integration
 * distinction does not quite anticipate.
 *
 * <p>It carries {@code eventId} and {@code occurredAt} regardless, because spec-01 &sect;2.6 says
 * every event must, and a rule with an exception carved into it for convenience is not a rule.
 */
public record VelocityBaselinePublishedEvent(UUID eventId,
                                             Instant occurredAt,
                                             String sku,
                                             double baselineVelocityPerHour)
        implements IntegrationEvent {

    public static final String TOPIC = "velocity-baseline-published";
}
