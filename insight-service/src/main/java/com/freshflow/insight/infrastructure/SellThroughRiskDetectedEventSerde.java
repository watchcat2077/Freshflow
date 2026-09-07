package com.freshflow.insight.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Serde for the {@code sell-through-risk-detected} output binding.
 *
 * <p>It supplies its own {@link ObjectMapper} for one reason, and the reason is a contract rather
 * than a preference. spec-01 &sect;2.6 requires {@code occurredAt} to be an <b>ISO-8601
 * timestamp</b>. Spring Kafka's default mapper writes {@code Instant} as an epoch decimal, so this
 * topic would have carried {@code 1788762590.000000000} where every other topic in the system
 * carries {@code 2026-09-07T06:29:50Z} &mdash; the same instant, in a format the contract does not
 * allow and a consumer written against the other topics would not parse.
 *
 * <p>The other topics are unaffected because they are produced through the message-channel binder,
 * which uses Spring Boot's auto-configured mapper and already writes ISO-8601. Only the Kafka
 * Streams output binding needed telling.
 */
public class SellThroughRiskDetectedEventSerde extends JsonSerde<SellThroughRiskDetectedEvent> {

    public SellThroughRiskDetectedEventSerde() {
        super(SellThroughRiskDetectedEvent.class, iso8601ObjectMapper());
        noTypeInfo();
    }

    private static ObjectMapper iso8601ObjectMapper() {
        ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
