package com.freshflow.insight.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test adapter for the {@code BriefingStore} port &mdash; the whole store, in a map, as
 * {@link InMemoryAgentTraceStore} is for FR14's traces and for the same reason: the orchestration
 * rules worth testing are statements about what was stored, and asserting them against a database
 * would test Hibernate rather than the use case.
 *
 * <p>Insertion order is preserved so that a test can say "exactly one briefing was written".
 */
public final class InMemoryBriefingStore implements BriefingStore {

    private final Map<String, EndOfDayBriefing> byBriefingId = new LinkedHashMap<>();

    @Override
    public void save(EndOfDayBriefing briefing) {
        byBriefingId.put(briefing.briefingId(), briefing);
    }

    @Override
    public Optional<EndOfDayBriefing> findById(String briefingId) {
        return Optional.ofNullable(byBriefingId.get(briefingId));
    }

    /** Every briefing written, in the order it was written. */
    public List<EndOfDayBriefing> all() {
        return new ArrayList<>(byBriefingId.values());
    }
}
