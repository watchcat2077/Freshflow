package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.BatchReceivedEvent;
import org.springframework.kafka.support.serializer.JsonSerde;

/** Serde for {@code batch-received}. Same arrangement as {@link ItemSoldEventSerde}. */
public class BatchReceivedEventSerde extends JsonSerde<BatchReceivedEvent> {

    public BatchReceivedEventSerde() {
        super(BatchReceivedEvent.class);
        ignoreTypeHeaders();
    }
}
