package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.MarginFloorPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers this context's domain services as beans.
 *
 * <p>The class exists because of a constraint, and the constraint is the point. spec-01 &sect;2.2
 * forbids the {@code domain} package from importing Spring, so {@link MarginFloorPolicy} cannot
 * annotate itself {@code @Service}; something outside the domain has to construct it, and
 * {@code infrastructure} is where wiring belongs. Same arrangement as {@code inventory-service}'s
 * {@code FefoDeductionPolicy}.
 *
 * <p>Because the policy knows nothing about Spring, {@code MarginFloorPolicyTest} exercises the
 * Anti-Corruption Layer's arithmetic with {@code new MarginFloorPolicy()} and no application
 * context at all.
 */
@Configuration
public class DomainServiceConfiguration {

    /** FR11's margin floor assessment (spec-03 &sect;3). Stateless, so one shared instance is correct. */
    @Bean
    public MarginFloorPolicy marginFloorPolicy() {
        return new MarginFloorPolicy();
    }
}
