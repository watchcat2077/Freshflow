package com.freshflow.insight.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the {@link Clock} the read side asks "now" from when deciding which window is current.
 *
 * <p>Injected rather than called statically, as in the other three services. It matters more here
 * than anywhere else: the whole of FR12 is a statement about time, and a service that reads the
 * system clock statically cannot be asked what it would say at a chosen instant.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
