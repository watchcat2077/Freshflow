package com.freshflow.insight.infrastructure;

import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Serde for {@code velocity-baseline-published}, the topic behind FR12's baseline
 * {@link org.apache.kafka.streams.kstream.KTable}.
 *
 * <p>Same arrangement and same reasoning as {@link ItemSoldEventSerde}: a named class so the
 * binding can reference it, and the target type fixed in code rather than taken from a header.
 */
public class VelocityBaselineSerde extends JsonSerde<VelocityBaselinePublishedEvent> {

    public VelocityBaselineSerde() {
        super(VelocityBaselinePublishedEvent.class);
        ignoreTypeHeaders();
    }
}
