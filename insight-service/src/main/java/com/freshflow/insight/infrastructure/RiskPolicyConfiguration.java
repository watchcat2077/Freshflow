package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.RiskAssessmentPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs {@link RiskAssessmentPolicy} with its configured thresholds.
 *
 * <p>The policy carries no Spring annotation, so that FR13's arithmetic can be exercised by
 * {@code new RiskAssessmentPolicy(5.0, 24.0)} with no application context. Wiring it is
 * infrastructure's job, exactly as with {@code inventory-service}'s domain service.
 *
 * <p>Not conditional on the Kafka toggle: the read side needs the policy's type to exist even in
 * the test profile, and building one is harmless without a broker.
 */
@Configuration
@EnableConfigurationProperties(RiskProperties.class)
public class RiskPolicyConfiguration {

    @Bean
    public RiskAssessmentPolicy riskAssessmentPolicy(RiskProperties properties) {
        return new RiskAssessmentPolicy(properties.getShortfallThreshold(),
                properties.getTradingHoursPerDay());
    }
}
