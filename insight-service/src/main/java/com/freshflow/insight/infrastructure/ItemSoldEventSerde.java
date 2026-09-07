package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Serde for the {@code item-sold} topic's Published Language payload.
 *
 * <p>Declared as a class with a no-argument constructor so that {@code application.yml} can name it
 * as the binding's {@code valueSerde}, rather than leaving the binder to infer a serde from generic
 * types.
 *
 * <p>{@code ignoreTypeHeaders()} is the part that matters. {@code sales-service} publishes through
 * the message-channel binder, which writes plain JSON with a {@code contentType} header and no
 * Spring Kafka {@code __TypeId__}. A deserializer that trusted type headers would find none and
 * fail; this one is told the target type up front, which is also the safer arrangement in general
 * &mdash; a consumer that instantiates whatever class a producer names in a header is trusting the
 * producer rather more than the Conformist relationship of spec-01 &sect;1.5 intends.
 */
public class ItemSoldEventSerde extends JsonSerde<ItemSoldEvent> {

    public ItemSoldEventSerde() {
        super(ItemSoldEvent.class);
        ignoreTypeHeaders();
    }
}
