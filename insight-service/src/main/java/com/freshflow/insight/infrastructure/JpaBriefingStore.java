package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.BriefingStore;
import com.freshflow.insight.application.EndOfDayBriefing;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter implementing the {@code BriefingStore} port (FR15).
 *
 * <p>The second thing this service genuinely persists, beside FR14's trace, and for the same
 * reason: a briefing is not derived from any stream, is written once when it is generated, and must
 * survive a restart so that {@code GET /api/insight/briefings/&#123;briefingId&#125;} can answer
 * tomorrow for a briefing written tonight.
 *
 * <p>The application layer is wired to {@code BriefingStore} and has no idea JPA is underneath,
 * which is what lets every orchestration test run against an in-memory store with no database and
 * no Spring context.
 *
 * <p><b>The methods are transactional</b> for the reason {@link JpaAgentTraceStore} gives:
 * {@code toolCalls} is a lazy {@code @OneToMany} that the mapper reads while turning an entity into
 * a record, and the controller calls {@code findById} with no transaction of its own.
 */
@Repository
public class JpaBriefingStore implements BriefingStore {

    private final BriefingJpaRepository jpaRepository;

    JpaBriefingStore(BriefingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** Writes the briefing and its calls in one transaction, the calls cascading from the briefing. */
    @Override
    @Transactional
    public void save(EndOfDayBriefing briefing) {
        throw new UnsupportedOperationException("FR15");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EndOfDayBriefing> findById(String briefingId) {
        throw new UnsupportedOperationException("FR15");
    }
}
