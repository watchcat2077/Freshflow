package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.MarkdownRecommendedEvent;
import com.freshflow.insight.application.MarkdownRecommendationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * The adapter behind {@link MarkdownRecommendationPublisher} &mdash; FR14's recommendation onto
 * {@code markdown-recommended} (spec-01 &sect;2.6, Table A).
 *
 * <p>Same shape as {@link VelocityBaselineSeeder}: a {@link StreamBridge} onto a message-channel
 * output binding, with the key in a header the binding's {@code messageKeyExpression} reads. That
 * is the producer pattern this service already runs, and running a second one would be a choice to
 * explain rather than a convenience.
 *
 * <p><b>Keyed by {@code batchId}.</b> Kafka guarantees order within a partition, so keying by the
 * batch is what keeps two recommendations for one batch in the order they were made. Keying by SKU
 * would put unrelated batches of one product in a queue behind each other, and keying by nothing
 * would spread one batch's history across partitions.
 *
 * <p><b>The binder's own {@code ObjectMapper} produces this topic</b>, which writes {@code Instant}
 * as ISO-8601 and so satisfies spec-01 &sect;2.6 without the special-cased mapper that
 * {@link SellThroughRiskDetectedEventSerde} needed. That serde exists because the Kafka Streams
 * output binding uses a different mapper (spec-05 &sect;7, G-6); this binding does not, and
 * {@code MarkdownRecommendedEventContractTest} is what proves the wire shape rather than the
 * absence of a comment saying so.
 *
 * <p><b>This is the far edge of FR14.</b> There is no adapter here for writing a price and none for
 * calling the Pricing context, because there is no port for either (R5, spec-06 &sect;7). What
 * leaves this service leaves on this topic, and is treated on arrival as an untrusted proposal
 * (spec-01 &sect;1.5).
 */
public class StreamBridgeMarkdownRecommendationPublisher implements MarkdownRecommendationPublisher {

    /** Binding name; its destination is {@link MarkdownRecommendedEvent#TOPIC}. */
    static final String BINDING = "markdownRecommended-out-0";

    private static final Logger log =
            LoggerFactory.getLogger(StreamBridgeMarkdownRecommendationPublisher.class);

    private final StreamBridge streamBridge;

    public StreamBridgeMarkdownRecommendationPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(MarkdownRecommendedEvent event) {
        Message<MarkdownRecommendedEvent> message = MessageBuilder.withPayload(event)
                .setHeader("batchId", event.batchId())
                .setHeader("eventId", event.eventId().toString())
                .build();

        if (streamBridge.send(BINDING, message)) {
            log.info("[published] topic={} eventId={} batchId={} tier={} agentTraceId={}",
                    MarkdownRecommendedEvent.TOPIC, event.eventId(), event.batchId(),
                    event.discountTier(), event.agentTraceId());
        } else {
            log.error("[publish-failed] topic={} batchId={} agentTraceId={}",
                    MarkdownRecommendedEvent.TOPIC, event.batchId(), event.agentTraceId());
        }
    }
}
