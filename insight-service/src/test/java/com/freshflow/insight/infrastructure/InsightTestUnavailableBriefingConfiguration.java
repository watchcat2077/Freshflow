package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceStore;
import com.freshflow.insight.application.AgentUnavailableException;
import com.freshflow.insight.application.BriefingAgent;
import com.freshflow.insight.application.BriefingOrchestrator;
import com.freshflow.insight.application.BriefingStore;
import com.freshflow.insight.application.BriefingToolRegistry;
import com.freshflow.insight.application.RiskQueryService;
import com.freshflow.insight.application.VelocityQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Test-profile wiring for the third state FR15 can be in: an agent <i>is</i> configured, and its
 * model cannot be used &mdash; a rate limit, a timeout, an authentication failure, a provider
 * outage. The {@link BriefingAgent} here throws {@link AgentUnavailableException} on every call,
 * which is what both LangChain4j adapters do when the provider throws.
 *
 * <p>Loaded only under {@code freshflow.insight.briefing.stub-agent=unavailable}, so the ordinary
 * {@code InsightTestBriefingConfiguration} (which loads for {@code true} or when the property is
 * absent) and the no-agent context ({@code false}) are unaffected. The bean names are not the
 * production ones, for the reason {@code InsightTestBriefingConfiguration} gives.
 */
@Configuration
@Profile("test")
@ConditionalOnProperty(name = "freshflow.insight.briefing.stub-agent", havingValue = "unavailable")
public class InsightTestUnavailableBriefingConfiguration {

    /** What the adapter's wrapped provider exception looks like once it reaches the controller. */
    public static final String REASON_FRAGMENT = "RateLimitException";

    @Bean
    public BriefingToolRegistry testUnavailableBriefingToolRegistry(
            RiskQueryService riskQueryService, AgentTraceStore agentTraceStore,
            VelocityQueryService velocityQueryService) {
        return new BriefingToolRegistry(riskQueryService, agentTraceStore, velocityQueryService);
    }

    @Bean
    public BriefingOrchestrator testUnavailableBriefingOrchestrator(
            BriefingToolRegistry tools, BriefingStore briefingStore,
            RiskQueryService riskQueryService, AgentTraceStore agentTraceStore, Clock clock) {
        BriefingAgent unavailable = (request, registry) -> {
            throw new AgentUnavailableException("FR15 briefing agent",
                    new IllegalStateException(REASON_FRAGMENT + ": quota exceeded (simulated)"));
        };
        return new BriefingOrchestrator(unavailable, tools, briefingStore, riskQueryService,
                agentTraceStore, clock);
    }
}
