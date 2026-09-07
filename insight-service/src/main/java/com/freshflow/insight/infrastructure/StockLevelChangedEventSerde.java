package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.StockLevelChangedEvent;
import org.springframework.kafka.support.serializer.JsonSerde;

/** Serde for {@code stock-level-changed}. Same arrangement as {@link ItemSoldEventSerde}. */
public class StockLevelChangedEventSerde extends JsonSerde<StockLevelChangedEvent> {

    public StockLevelChangedEventSerde() {
        super(StockLevelChangedEvent.class);
        ignoreTypeHeaders();
    }
}
