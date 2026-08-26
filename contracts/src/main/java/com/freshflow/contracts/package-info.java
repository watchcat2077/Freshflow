/**
 * Published Language (spec-01 &sect;1.5) &mdash; the shared event contract between bounded
 * contexts. Every event defined here carries {@code eventId} (UUID, for idempotent
 * consumption) and {@code occurredAt} (producer-assigned event time, used for windowing)
 * per spec-01 &sect;2.6.
 *
 * <p>Skeleton only (Phase 2). Event types are added in the phase that first needs them.
 */
package com.freshflow.contracts;
