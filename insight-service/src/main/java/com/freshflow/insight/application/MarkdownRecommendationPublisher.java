package com.freshflow.insight.application;

import com.freshflow.contracts.MarkdownRecommendedEvent;

/**
 * Outbound port &mdash; publishes FR14's recommendation onto {@code markdown-recommended}
 * (spec-01 &sect;2.6, Table A).
 *
 * <p>Declared in {@code application} (spec-01 &sect;2.3, ADR 0002 Decision 2); the Spring Cloud
 * Stream adapter behind it lives in {@code infrastructure} and is Phase B work. Single method,
 * returning nothing: this is a write at a boundary the service does not own.
 *
 * <p><b>This is the far edge of what FR14 is permitted to do.</b> The agent proposes and stops.
 * There is deliberately no port here for calling the Pricing context, no port for writing a price
 * and no port for reading a margin floor, because spec-01 &sect;1.5 makes Pricing an
 * Anti-Corruption Layer against exactly this producer: it independently validates the margin floor
 * and treats what arrives on this topic as an untrusted proposal rather than an authoritative
 * command (spec-02 FR11, spec-01 &sect;2.5). A second outbound port from this service into Pricing
 * would not be a shortcut, it would dismantle the project's central argument.
 *
 * <p>The publication is performed by {@link MarkdownAgentOrchestrator}, once per trace, rather
 * than by the {@code proposeMarkdown} tool itself. spec-06 &sect;5 gives the reason: how many times
 * a language model chooses to call a tool must not decide how many events reach another bounded
 * context.
 */
public interface MarkdownRecommendationPublisher {

    void publish(MarkdownRecommendedEvent event);
}
