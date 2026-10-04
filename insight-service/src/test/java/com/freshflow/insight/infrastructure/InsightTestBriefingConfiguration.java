package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceStore;
import com.freshflow.insight.application.BriefingOrchestrator;
import com.freshflow.insight.application.BriefingStore;
import com.freshflow.insight.application.BriefingToolRegistry;
import com.freshflow.insight.application.InMemoryBriefingStore;
import com.freshflow.insight.application.RiskQueryService;
import com.freshflow.insight.application.StubBriefingAgent;
import com.freshflow.insight.application.VelocityQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Test-profile wiring for FR15: a {@code BriefingOrchestrator} over the deterministic
 * {@link StubBriefingAgent}, an in-memory {@code BriefingStore}, and the fixtures the other
 * test-profile configurations already supply &mdash; the fixed clock and the risk, velocity and
 * trace stores. It stands in for {@code BriefingAgentConfiguration}, which is
 * {@code @Profile("!test")} and never loads here.
 *
 * <p><b>The bean names are deliberately not the production ones.</b>
 * {@code MarkdownAgentTestProfileGuardTest} asserts that no test context contains a bean named
 * {@code briefingOrchestrator} or {@code briefingAgent}, because those are the names the
 * LLM-backed configuration declares and their presence would mean it had loaded. The beans here
 * are {@code testBriefingOrchestrator} and friends; the controller resolves the orchestrator by
 * type, so the name is free to say what the bean is.
 *
 * <p><b>Switched off by {@code freshflow.insight.briefing.stub-agent=false}</b>, which is how
 * {@code BriefingApiIntegrationTest} obtains a context with no orchestrator at all and asserts
 * spec-02 FR15's "with no LLM configured the endpoint answers 503 and the service otherwise runs
 * normally". With the property absent the configuration loads, as every other test expects.
 */
@Configuration
@Profile("test")
@ConditionalOnProperty(name = "freshflow.insight.briefing.stub-agent", havingValue = "true",
        matchIfMissing = true)
public class InsightTestBriefingConfiguration {

    @Bean
    @Primary
    public BriefingStore testBriefingStore() {
        return new InMemoryBriefingStore();
    }

    @Bean
    public BriefingToolRegistry testBriefingToolRegistry(RiskQueryService riskQueryService,
                                                         AgentTraceStore agentTraceStore,
                                                         VelocityQueryService velocityQueryService) {
        return new BriefingToolRegistry(riskQueryService, agentTraceStore, velocityQueryService);
    }

    /**
     * The stub looks up {@link InsightTestVelocityConfiguration#SKU_ACTIVE}, which the velocity
     * fixture answers, so every recorded call in the endpoint test carries a real result.
     */
    @Bean
    public BriefingOrchestrator testBriefingOrchestrator(BriefingToolRegistry tools,
                                                         BriefingStore briefingStore,
                                                         RiskQueryService riskQueryService,
                                                         AgentTraceStore agentTraceStore,
                                                         Clock clock) {
        return new BriefingOrchestrator(
                StubBriefingAgent.summarising(clock, InsightTestVelocityConfiguration.SKU_ACTIVE),
                tools, briefingStore, riskQueryService, agentTraceStore, clock);
    }
}
