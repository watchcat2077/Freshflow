package com.freshflow.insight.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Publishes the configured FR12 velocity baselines onto
 * {@code velocity-baseline-published}, which is the topic the topology's {@code KTable} is built
 * from.
 *
 * <p><b>This class is the specification gap made concrete.</b> spec-02 FR12 requires the current
 * rate to be compared "against a stored velocity baseline held in a KTable" and no requirement says
 * where that baseline comes from. Until the team decides, it comes from configuration &mdash;
 * {@code freshflow.insight.velocity.baselines}, recorded in spec-01 &sect;2.7 next to the price-rule
 * seed. The eventual answer is presumably a computed historical average, and when it arrives it
 * replaces this class and nothing else: the topology, the stores, the ports and the endpoint all
 * consume the topic, not the configuration.
 *
 * <p>Published on {@link ApplicationReadyEvent} rather than {@code @PostConstruct}, for the same
 * reason as {@code pricing-service}'s seeder: publishing crosses a process boundary and must wait
 * until the binder is up. Republishing on every start is safe because the KTable is keyed by SKU,
 * so a repeat is an idempotent overwrite of the same key rather than a duplicate row.
 */
@Component
@EnableConfigurationProperties(VelocityProperties.class)
@ConditionalOnProperty(name = "freshflow.messaging.kafka.enabled", havingValue = "true",
        matchIfMissing = true)
public class VelocityBaselineSeeder {

    /** Binding name; its destination is {@link VelocityBaselinePublishedEvent#TOPIC}. */
    static final String BINDING = "velocityBaseline-out-0";

    private static final Logger log = LoggerFactory.getLogger(VelocityBaselineSeeder.class);

    private final VelocityProperties properties;
    private final StreamBridge streamBridge;
    private final Clock clock;

    public VelocityBaselineSeeder(VelocityProperties properties, StreamBridge streamBridge,
                                  Clock clock) {
        this.properties = properties;
        this.streamBridge = streamBridge;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishConfiguredBaselines() {
        int published = 0;

        for (VelocityProperties.Baseline baseline : properties.getBaselines()) {
            if (baseline.getSku() == null || baseline.getSku().isBlank()) {
                log.warn("Skipping a freshflow.insight.velocity.baselines entry with no sku.");
                continue;
            }

            VelocityBaselinePublishedEvent payload = new VelocityBaselinePublishedEvent(
                    UUID.randomUUID(),
                    clock.instant(),
                    baseline.getSku(),
                    baseline.getBaselineVelocityPerHour());

            // Keyed by SKU, which is what makes the KTable a table: one row per SKU, last write
            // winning, rather than an ever-growing log of baselines.
            Message<VelocityBaselinePublishedEvent> message = MessageBuilder.withPayload(payload)
                    .setHeader("sku", payload.sku())
                    .setHeader("eventId", payload.eventId().toString())
                    .build();

            if (streamBridge.send(BINDING, message)) {
                published++;
                log.info("[published] topic={} eventId={} sku={} baselineVelocityPerHour={}",
                        VelocityBaselinePublishedEvent.TOPIC, payload.eventId(), payload.sku(),
                        payload.baselineVelocityPerHour());
            } else {
                log.error("[publish-failed] topic={} sku={}",
                        VelocityBaselinePublishedEvent.TOPIC, payload.sku());
            }
        }

        log.info("Seeded {} of {} velocity baseline(s) from freshflow.insight.velocity.baselines",
                published, properties.getBaselines().size());
    }
}
