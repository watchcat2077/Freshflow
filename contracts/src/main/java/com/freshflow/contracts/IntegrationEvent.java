package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * What spec-01 &sect;2.6 requires of every event, expressed so the compiler enforces it:
 *
 * <blockquote>
 * Every event MUST carry {@code eventId} (UUID) &mdash; used for idempotent consumption &mdash;
 * and {@code occurredAt} (ISO-8601 timestamp) &mdash; event time, written by the producer, used
 * for windowing. MUST NOT rely on Kafka ingestion time.
 * </blockquote>
 *
 * <p>Introduced in Week 7, when a third and fourth topic needed to be windowed on event time and
 * the alternative was a timestamp extractor that pattern-matched on every payload type in turn.
 * A rule restated in four javadoc comments is a convention; a rule in the type system is a rule.
 *
 * <p>Internal topics implement it too. spec-01 &sect;2.6's Table B distinguishes internal topics
 * from integration topics by <i>who may depend on the payload</i>, not by which rules apply to
 * it, and the "every event MUST carry" clause admits no exception. An internal event may
 * therefore implement this interface without thereby becoming part of the Published Language.
 */
public interface IntegrationEvent {

    /** Identifies this publication. Consumers deduplicate on it (spec-01 &sect;2.6). */
    UUID eventId();

    /**
     * Producer-assigned event time. Windowing and every other time calculation use this, never
     * Kafka ingestion time (spec-01 &sect;2.6).
     */
    Instant occurredAt();
}
