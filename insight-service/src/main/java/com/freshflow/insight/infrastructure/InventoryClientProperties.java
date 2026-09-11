package com.freshflow.insight.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where {@link RestBatchDetailsProvider} finds {@code inventory-service}.
 *
 * <p>Configuration rather than a literal, for the reason spec-01 &sect;2.7 gives for every other
 * value in this service: the specification fixes the port at 8081 for local running (spec-01
 * &sect;1.2) and a deployment that moves it should be a YAML change, not a code change. It arrives
 * from {@code ${INVENTORY_BASE_URL}} so that a containerised run can point at a service name
 * without rebuilding anything.
 *
 * <p>This is the only outbound HTTP destination {@code insight-service} has, and spec-01 &sect;1.3
 * permits it for exactly one purpose: agent tool calls. There is deliberately no property here for
 * any other service &mdash; in particular none for the Pricing context, which R5 and spec-06
 * &sect;7 forbid this service to call at all.
 */
@ConfigurationProperties(prefix = "freshflow.insight.inventory")
public class InventoryClientProperties {

    /** spec-01 s1.2: inventory-service listens on 8081. */
    private String baseUrl = "http://localhost:8081";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
