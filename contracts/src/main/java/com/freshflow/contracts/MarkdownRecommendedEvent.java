package com.freshflow.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event for topic {@code markdown-recommended} (spec-01 &sect;2.6, Table A).
 * Producer: insight. Consumer: pricing.
 *
 * <p>This is the one event that crosses the boundary the whole project is built around. FR14's
 * Markdown Decision Agent <b>proposes only</b>: it publishes this, and stops. Whether the proposal
 * becomes a price is {@code pricing-service}'s decision alone, taken in its domain layer against
 * the margin floor (FR11, spec-01 &sect;1.5's Anti-Corruption Layer, spec-01 &sect;2.5). A
 * recommendation is therefore exactly what its name says: untrusted input to a context that
 * validates it, never a command.
 *
 * <p>Field set is spec-01 &sect;2.6 Table A's row for this topic &mdash; batchId, sku,
 * discountTier, effectiveFrom, effectiveTo, rationale, agentTraceId &mdash; plus the
 * {@code eventId} and {@code occurredAt} that {@link IntegrationEvent} requires of every event.
 *
 * <p>{@code rationale} is FR14's "written rationale" and {@code agentTraceId} is the key to the
 * full tool-call trace behind it, retrievable at
 * {@code GET /api/insight/agent-traces/&#123;agentTraceId&#125;} (spec-04). Between them they are
 * what makes an automated markdown auditable rather than merely automatic: a consumer that rejects
 * this proposal, and a supervisor reviewing one that was accepted, can both ask why it was made.
 *
 * <p>{@code occurredAt} is the instant the agent completed its deliberation, assigned by the
 * producer as spec-01 &sect;2.6 requires. {@code effectiveFrom} and {@code effectiveTo} are the
 * proposed promotion window and are a different thing entirely: they are when the markdown would
 * run, not when it was recommended, and no rule orders them against {@code occurredAt}.
 */
public record MarkdownRecommendedEvent(UUID eventId,
                                       Instant occurredAt,
                                       String batchId,
                                       String sku,
                                       DiscountTier discountTier,
                                       Instant effectiveFrom,
                                       Instant effectiveTo,
                                       String rationale,
                                       String agentTraceId) implements IntegrationEvent {

    public static final String TOPIC = "markdown-recommended";
}
