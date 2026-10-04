package com.freshflow.insight.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * What the briefing agent is asked for (FR15): the business date to brief on, and the instant the
 * request was made.
 *
 * <p>{@code businessDate} is a calendar day, and the day is a <b>UTC</b> day: spec-04's
 * {@code BriefingRequest} says "Defaults to today in UTC when the body is omitted". The two
 * accessors below turn that day into the half-open instant range
 * {@code [businessDayStart, businessDayEnd)} that FR15's recommendation count and its
 * {@code listRecommendations} tool both read against. The arithmetic is stated once, here, so that
 * the evidence the model reads and the number the orchestrator computes can never disagree about
 * where a day begins.
 *
 * <p>{@code now} is wall-clock, from the orchestrator's injected {@link java.time.Clock}, and is
 * given to the agent so that "still at risk tonight" has a referent. It is the same instant the
 * briefing is stamped {@code generatedAt} with. Like FR14's {@code completedAt} (spec-06 &sect;6),
 * it is a fact about this service's own operation and is legitimately not event time.
 */
public record BriefingRequest(LocalDate businessDate, Instant now) {

    /** The first instant of {@code businessDate}, in UTC. Inclusive. */
    public Instant businessDayStart() {
        return startOfUtcDay(businessDate);
    }

    /** The first instant of the day after {@code businessDate}, in UTC. Exclusive. */
    public Instant businessDayEnd() {
        return startOfUtcDay(businessDate.plusDays(1));
    }

    /** spec-04: a business date is a UTC calendar day. The one place that becomes arithmetic. */
    public static Instant startOfUtcDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
