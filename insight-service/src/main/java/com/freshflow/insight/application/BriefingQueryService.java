package com.freshflow.insight.application;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application service &mdash; the read-side use case behind
 * {@code GET /api/insight/briefings/&#123;briefingId&#125;} (FR15, spec-04 {@code getBriefing}).
 *
 * <p>Separate from {@link BriefingOrchestrator} on purpose. The orchestrator exists only when a
 * language model is configured, because it needs one; this service exists always, because reading
 * a stored briefing needs nothing but the store. spec-02 FR15 requires that "with no LLM configured
 * the endpoint answers 503 and the service otherwise runs normally" &mdash; the POST is the
 * endpoint that answers 503, and the GET is part of "otherwise runs normally".
 *
 * <p>It decides nothing: a stored briefing is returned as it was written. The audit value of the
 * record, like FR14's trace, lies in its not being reinterpreted after the fact.
 */
@Service
public class BriefingQueryService {

    private final BriefingStore briefingStore;

    public BriefingQueryService(BriefingStore briefingStore) {
        this.briefingStore = briefingStore;
    }

    /** The briefing with this identifier, or empty &mdash; spec-04's 404. */
    public Optional<EndOfDayBriefing> findById(String briefingId) {
        return briefingStore.findById(briefingId);
    }
}
