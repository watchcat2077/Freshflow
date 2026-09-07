package com.freshflow.inventory.infrastructure;

import com.freshflow.inventory.domain.FefoDeductionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers this context's domain services as beans.
 *
 * <p>The class exists because of a constraint, and the constraint is the point. spec-01 &sect;2.2
 * forbids the {@code domain} package from importing Spring, so {@link FefoDeductionPolicy} cannot
 * annotate itself {@code @Service} &mdash; the annotation would be exactly the kind of framework
 * dependency the layering rules out. Something outside the domain therefore has to construct it,
 * and {@code infrastructure} is where wiring belongs.
 *
 * <p>That is not a workaround for the rule; it is what the rule buys. Because the policy knows
 * nothing about Spring, {@code FefoDeductionPolicyTest} exercises FEFO with {@code new
 * FefoDeductionPolicy()} and no application context at all.
 */
@Configuration
public class DomainServiceConfiguration {

    /**
     * FR2's FEFO selection (spec-03 &sect;1). Stateless, so a single shared instance is correct.
     */
    @Bean
    public FefoDeductionPolicy fefoDeductionPolicy() {
        return new FefoDeductionPolicy();
    }
}
