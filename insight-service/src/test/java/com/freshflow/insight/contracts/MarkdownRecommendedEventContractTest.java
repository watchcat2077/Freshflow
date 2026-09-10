package com.freshflow.insight.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshflow.contracts.DiscountTier;
import com.freshflow.contracts.IntegrationEvent;
import com.freshflow.contracts.MarkdownRecommendedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Published Language contract for {@code markdown-recommended} (spec-01 &sect;2.6, Table A).
 *
 * <p>The second test here is the cheap half of known limitation G-6 (spec-05 &sect;7), and is in
 * scope for that reason. G-6 records that spec-01 &sect;2.6 requires {@code occurredAt} to be an
 * ISO-8601 timestamp and that <b>nothing enforces it</b>: the type system enforces that the field
 * exists, not how it is serialised. Week 7 part 2 found that the expensive way, when
 * {@code sell-through-risk-detected} shipped {@code occurredAt} as {@code 1788762590.000000000}
 * &mdash; the right instant in a format the clause does not allow &mdash; and no test failed,
 * because no test serialised that topic.
 *
 * <p>{@code markdown-recommended} is the one topic this project publishes across a real context
 * boundary to a consumer that will enforce a business rule on what it receives, so it is the worst
 * possible place to discover a wire-format defect. G-6's full fix is a contract test over every
 * event in the {@code contracts} module; that module has no test dependencies today, and adding
 * them is a {@code pom.xml} change this phase does not permit. This test covers the one new event.
 *
 * <p><b>The {@link ObjectMapper} is the application's own, autowired from a {@code @JsonTest}
 * slice.</b> That is the whole point, and the first draft of this test got it wrong in an
 * instructive way: a mapper built by hand in the test asserts only that Jackson <i>can</i> be told
 * to produce ISO-8601, and Jackson's own default is the epoch number G-6 is about. What has to be
 * asserted is what the binder publishing this event will actually produce, which is the mapper
 * Spring Boot auto-configures.
 *
 * <p>Note the scope of that claim. This event is published through the message-channel binder, so
 * the Boot mapper is the right one to assert against. spec-05 &sect;6 records that the Kafka Streams
 * serdes use a different mapper, and that difference is what caused G-6; a test over a Streams
 * output topic would have to reach that serde instead.
 */
@JsonTest
@ActiveProfiles("test")
@DisplayName("spec-01 s2.6: markdown-recommended event contract (FR14)")
class MarkdownRecommendedEventContractTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-10T09:30:00Z");

    /** spec-01 s2.6's own words: "occurredAt (ISO-8601 timestamp)". */
    private static final String ISO_8601_INSTANT =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    @Autowired
    private ObjectMapper objectMapper;

    private static MarkdownRecommendedEvent event() {
        return new MarkdownRecommendedEvent(
                UUID.fromString("6f1b3a52-2c44-4f6a-8f0e-1d9b7c5a2e10"),
                OCCURRED_AT,
                "BAT-0001",
                "SKU-YOG-1KG",
                DiscountTier.PERCENT_30,
                Instant.parse("2026-09-10T09:30:00Z"),
                Instant.parse("2026-09-10T18:30:00Z"),
                "41 units remaining, 9 trading hours to expiry, velocity 2.0/h.",
                "TRC-0001");
    }

    /**
     * spec-01 &sect;2.6 Table A fixes this topic's key fields, and the "every event MUST carry"
     * clause fixes the other two. Asserted by reflection over the record components rather than by
     * calling each accessor, so that a field removed from the contract fails here rather than
     * failing to compile somewhere downstream months later.
     */
    @Test
    @DisplayName("carries every spec-01 s2.6 field and implements IntegrationEvent")
    void carriesEverySpecifiedFieldAndIsAnIntegrationEvent() {
        List<String> components = Arrays.stream(MarkdownRecommendedEvent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("spec-01 s2.6 Table A: batchId, sku, discountTier, effectiveFrom, effectiveTo, "
                        + "rationale, agentTraceId; plus eventId and occurredAt, which every event "
                        + "must carry")
                .containsExactlyInAnyOrder("eventId", "occurredAt", "batchId", "sku",
                        "discountTier", "effectiveFrom", "effectiveTo", "rationale",
                        "agentTraceId");

        assertThat(IntegrationEvent.class)
                .as("the two mandatory fields are enforced by the type system, not by convention")
                .isAssignableFrom(MarkdownRecommendedEvent.class);

        assertThat(MarkdownRecommendedEvent.TOPIC).isEqualTo("markdown-recommended");

        assertThat(DiscountTier.values())
                .as("spec-04 enumerates exactly these four tiers and admits no others")
                .containsExactly(DiscountTier.PERCENT_20, DiscountTier.PERCENT_30,
                        DiscountTier.PERCENT_50, DiscountTier.PERCENT_60);
    }

    /**
     * G-6, for this one event: {@code occurredAt} on the wire must be an ISO-8601 string, not an
     * epoch number, and {@code eventId} must be a UUID string.
     */
    @Test
    @DisplayName("occurredAt serialises as an ISO-8601 string, not an epoch number")
    void occurredAtSerialisesAsIso8601() throws Exception {
        String json = objectMapper.writeValueAsString(event());

        String occurredAt = objectMapper.readTree(json).get("occurredAt").asText();
        assertThat(occurredAt)
                .as("spec-01 s2.6 requires an ISO-8601 timestamp; a numeric epoch is the exact "
                        + "defect recorded as known limitation G-6")
                .matches(ISO_8601_INSTANT);
        assertThat(Instant.parse(occurredAt)).isEqualTo(OCCURRED_AT);

        String eventId = objectMapper.readTree(json).get("eventId").asText();
        assertThat(UUID.fromString(eventId)).isEqualTo(event().eventId());
    }
}
