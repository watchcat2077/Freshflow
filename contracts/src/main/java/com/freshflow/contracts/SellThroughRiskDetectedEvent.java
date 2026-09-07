package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event for topic {@code sell-through-risk-detected} (spec-01 &sect;2.6, Table A).
 * Producer: insight. Consumer: insight.
 *
 * <p>Both ends are the same context today, and it is still an integration topic rather than an
 * internal one. spec-01 &sect;2.6 says so explicitly: FR14's Markdown Decision Agent consumes this
 * signal and {@code markdown-recommended} carries its conclusion to {@code pricing}, so the risk
 * signal is part of the vocabulary the contexts share even while the loop is closed inside Insight.
 *
 * <p>Emitted when a batch <b>newly becomes</b> at risk, not repeatedly for as long as it stays
 * there. It is a detection, and a detection happens once; the standing list of what is currently
 * at risk is the read model behind {@code GET /api/insight/risks}.
 *
 * <p>{@code occurredAt} is the end of the velocity window the assessment was made from &mdash;
 * event time, not the moment the topology got round to emitting it (spec-01 &sect;2.6). Every
 * figure in the event is measured from that same instant.
 */
public record SellThroughRiskDetectedEvent(UUID eventId,
                                           Instant occurredAt,
                                           String batchId,
                                           String sku,
                                           int remainingQuantity,
                                           long hoursToExpiry,
                                           double currentVelocity,
                                           double projectedShortfall) implements IntegrationEvent {

    public static final String TOPIC = "sell-through-risk-detected";
}
