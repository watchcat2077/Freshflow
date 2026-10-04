package com.freshflow.insight.presentation;

import java.time.LocalDate;

/**
 * spec-04 {@code BriefingRequest}: the one optional property of {@code POST /api/insight/briefings}.
 *
 * <p>{@code businessDate} is "the day to brief on", an ISO-8601 date, and "defaults to today in UTC
 * when the body is omitted". The default is applied by the orchestrator, not here: a controller
 * that read the clock would be deciding what day it is, and the orchestrator already owns that
 * decision so that a test can fix it.
 */
public record BriefingRequestDto(LocalDate businessDate) {
}
