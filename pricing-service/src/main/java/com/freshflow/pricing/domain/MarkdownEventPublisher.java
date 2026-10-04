package com.freshflow.pricing.domain;

/**
 * Outbound port (spec-01 &sect;2.2) &mdash; how the Pricing &amp; Markdown context announces that a
 * markdown is in force.
 *
 * <p>Declared in {@code domain} and implemented in {@code infrastructure}, beside
 * {@link PriceRuleEventPublisher} and for the same reason: this service is Domain Model, and its
 * messaging ports live in the domain (ADR 0002). ADR 0005 moved the <i>repository</i> interfaces
 * to {@code infrastructure.repository} and said nothing about messaging ports, so this one stays
 * where its sibling is.
 *
 * <p>A separate port from {@code PriceRuleEventPublisher} rather than a second method on it,
 * because the two announce different things to different consumers: the standing rule to Sales,
 * and a live markdown to Sales and Insight. The Kafka adapter behind it can be replaced without
 * the domain or the application layer noticing; the test profile does exactly that.
 */
public interface MarkdownEventPublisher {

    /** FR9 &mdash; "Publishes {@code markdown-applied} on success." */
    void publish(MarkdownApplied event);
}
