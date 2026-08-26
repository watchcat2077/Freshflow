package com.freshflow.inventory.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the {@link Clock} the application layer reads "now" from.
 *
 * <p>Injected rather than called statically so that time is a dependency like any other. The domain
 * never asks what time it is &mdash; every rule that needs an instant is handed one, which is what
 * keeps {@code StockBatch} deterministic and testable without a clock abstraction of its own.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
