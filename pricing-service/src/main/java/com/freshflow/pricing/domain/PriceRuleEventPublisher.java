package com.freshflow.pricing.domain;

/**
 * Outbound port (spec-01 &sect;2.2) &mdash; how the Pricing &amp; Markdown context announces the
 * price rule in force for a SKU.
 *
 * <p>Declared in {@code domain} and implemented in {@code infrastructure}. spec-01 &sect;2.3 is
 * explicit about the placement: this service is <b>Domain Model</b>, and for Domain Model services
 * event ports &mdash; inbound and outbound alike &mdash; are declared in {@code domain}. (The
 * repository interfaces moved to {@code infrastructure.repository} on 29 September 2026, ADR 0005;
 * the coordinator's ruling was about repositories and this port stays.) That is why this port sits here while
 * {@code sales-service}'s equivalent sits in its application layer: the layer follows the
 * service's pattern, not a global convention (ADR 0001, ADR 0002).
 *
 * <p>Like every port, it names what the inner layer needs and says nothing about how. The Kafka
 * adapter behind it can be replaced without the domain or the application layer noticing.
 */
public interface PriceRuleEventPublisher {

    void publish(PriceRulePublished event);
}
