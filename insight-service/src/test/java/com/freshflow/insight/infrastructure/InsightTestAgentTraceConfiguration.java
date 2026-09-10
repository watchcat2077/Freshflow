package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.AgentTraceStore;
import com.freshflow.insight.application.InMemoryAgentTraceStore;
import com.freshflow.insight.application.ToolCall;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.util.List;

/**
 * Test-scope adapter for the {@code AgentTraceStore} port, seeded with one trace.
 *
 * <p>The same device {@link InsightTestVelocityConfiguration} uses for FR12 and FR13, and for the
 * same reason: spec-04's endpoint contract is a statement about HTTP, and asserting it through a
 * database would be asserting Hibernate as well. The persistence adapter has its own slice test.
 *
 * <p>{@code @Primary} because {@code JpaAgentTraceStore} is also an {@code AgentTraceStore} and is
 * a {@code @Repository}; both beans exist under the test profile and this one wins injection.
 */
@Configuration
@Profile("test")
public class InsightTestAgentTraceConfiguration {

    /** The {@code sell-through-risk-detected} eventId that produced this trace. */
    public static final String SEEDED_TRIGGER_EVENT_ID = "0a5f2f3e-9c31-4a7d-9b52-6d4f1f9d2c77";

    /**
     * The trace the endpoint test asks for. It is the trigger eventId with a prefix, because
     * spec-06 s5 derives the identifier from the trigger rather than minting a fresh one: a
     * redelivered trigger then collides on the primary key itself and not merely on a secondary
     * constraint. spec-04 illustrates the field with {@code "TRC-0001"} and types it as a plain
     * string, so the format is ours to choose.
     */
    public static final String SEEDED_TRACE_ID = "TRC-" + SEEDED_TRIGGER_EVENT_ID;

    public static final String SEEDED_BATCH_ID = "BAT-0001";

    public static final Instant COMPLETED_AT = Instant.parse("2026-09-10T09:30:00Z");

    public static final String FINAL_RECOMMENDATION =
            "PERCENT_30 from 2026-09-10T09:30:00Z until 2026-09-10T18:30:00Z";

    public static final String RATIONALE =
            "41 units remaining, 9 trading hours to expiry, velocity 2.0/h.";

    /**
     * Three calls in the order the agent made them, which is the order spec-04's {@code sequence}
     * records and the order the endpoint must return them in.
     */
    public static final AgentTraceRecord SEEDED_TRACE = new AgentTraceRecord(
            SEEDED_TRACE_ID,
            SEEDED_TRIGGER_EVENT_ID,
            SEEDED_BATCH_ID,
            List.of(
                    new ToolCall(1, "getBatchDetails", "BAT-0001",
                            "remainingQuantity=41 expiryAt=2026-09-10T18:30:00Z unitCost=2.10 AUD",
                            Instant.parse("2026-09-10T09:29:58Z")),
                    new ToolCall(2, "getSellThroughRate", "SKU-YOG-1KG",
                            "velocityPerHour=120.0 baselineVelocity=120.0 velocityRatio=1.0",
                            Instant.parse("2026-09-10T09:29:59Z")),
                    new ToolCall(3, "proposeMarkdown",
                            "BAT-0001,PERCENT_30,2026-09-10T09:30:00Z", "recorded",
                            COMPLETED_AT)),
            FINAL_RECOMMENDATION,
            RATIONALE,
            COMPLETED_AT);

    @Bean
    @Primary
    public AgentTraceStore testAgentTraceStore() {
        InMemoryAgentTraceStore store = new InMemoryAgentTraceStore();
        store.save(SEEDED_TRACE);
        return store;
    }
}
