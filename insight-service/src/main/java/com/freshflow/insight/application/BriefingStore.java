package com.freshflow.insight.application;

import java.util.Optional;

/**
 * Outbound port &mdash; persistence for FR15's end-of-day briefings.
 *
 * <p>Declared in {@code application} for the same reason as {@link AgentTraceStore} and every
 * other port in this service (spec-01 &sect;2.3, ADR 0002 Decision 2, ADR 0004). Its adapter is
 * {@code JpaBriefingStore} in {@code infrastructure}; the entity and {@link EndOfDayBriefing} are
 * two different types with a mapper between them because spec-01 &sect;2.2 forbids this layer from
 * importing JPA.
 *
 * <p>Two methods and no more. {@code save} is write-only: a briefing is written once, when it is
 * generated, and never updated. {@code findById} serves spec-04's
 * {@code GET /api/insight/briefings/&#123;briefingId&#125;}. There is no finder by date, because
 * no endpoint asks for one and a port that offers what nobody reads invites a use nobody reviewed.
 * The argument {@code AgentTraceStore}'s javadoc makes for why a CQRS read-side port may have a
 * finder at all applies here unchanged.
 */
public interface BriefingStore {

    void save(EndOfDayBriefing briefing);

    Optional<EndOfDayBriefing> findById(String briefingId);
}
