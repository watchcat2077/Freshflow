package com.freshflow.pricing.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the {@link Clock} the application layer stamps producer-assigned event time from
 * (spec-01 &sect;2.6).
 *
 * <p>Injected rather than called statically, for the same reason as in {@code inventory-service}:
 * time is a dependency like any other, and the domain never asks what time it is &mdash; it is
 * handed the instant.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
