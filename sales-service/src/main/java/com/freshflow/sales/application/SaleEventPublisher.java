package com.freshflow.sales.application;

import com.freshflow.sales.domain.SaleTransaction;

/**
 * Outbound port &mdash; how this context announces that items were sold (FR5: "Publishes one
 * item-sold event per line item").
 *
 * <p>Declared in the application layer rather than the domain layer, which differs from
 * {@code inventory-service} and {@code pricing-service}. The reason is ADR 0001 applied
 * consistently: this service is Transaction Script, its domain package holds plain data structures
 * and declares no interface at all, and its behaviour lives here. Putting the inbound port in
 * {@code application} and the outbound port in {@code domain} would split the service's ports
 * across two layers for no reason. The two Domain Model services keep their publisher port in
 * {@code domain}, where their behaviour is.
 *
 * <p>The adapter behind it is {@code KafkaSaleEventPublisher} in {@code infrastructure}; the
 * transaction script that calls this interface knows nothing of Kafka.
 */
public interface SaleEventPublisher {

    void publishItemSold(SaleTransaction transaction);
}
