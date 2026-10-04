package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.AgentTraceStore;
import com.freshflow.insight.application.BriefingAgent;
import com.freshflow.insight.application.BriefingOrchestrator;
import com.freshflow.insight.application.BriefingStore;
import com.freshflow.insight.application.BriefingToolRegistry;
import com.freshflow.insight.application.RiskQueryService;
import com.freshflow.insight.application.VelocityQueryService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * FR15's runtime wiring &mdash; the tool registry, the adapter and the orchestrator, under the
 * same two guards as {@link MarkdownAgentConfiguration} and for the same reasons.
 *
 * <p><b>{@code @Profile("!test")}, on the outer class and repeated on the nested one.</b> Every
 * test in this service runs under {@code @ActiveProfiles("test")}, so nothing here is built by any
 * test context; that is the mechanical form of ADR 0004's promise that no test resolves a live
 * model (R10.1). The repetition on the nested class is not redundant: a {@code static} nested
 * {@code @Configuration} is a component-scan candidate in its own right and does not inherit the
 * enclosing class's {@code @Profile}. {@code MarkdownAgentTestProfileGuardTest} asserts, by bean
 * name, that neither {@code briefingAgent} nor {@code briefingOrchestrator} exists in a test
 * context even with an API key configured.
 *
 * <p><b>{@code @ConditionalOnExpression} on a non-empty API key.</b> With no credential there is
 * no model, and the nested class is not loaded at all: {@code insight-service} still starts and
 * still serves FR12, FR13 and FR14's trace endpoint, and {@code BriefingController} answers
 * {@code POST /api/insight/briefings} with 503 {@code AGENT_NOT_CONFIGURED} because it finds no
 * {@link BriefingOrchestrator} to delegate to (spec-02 FR15: "with no LLM configured the endpoint
 * answers 503 and the service otherwise runs normally").
 *
 * <p><b>The model bean is FR14's.</b> {@code MarkdownAgentConfiguration.LlmBackedAgentConfiguration}
 * builds the one {@link ChatModel} this service has, under the same condition as the nested class
 * below, so the two configurations stand or fall together and one credential serves both agents.
 * spec-01 &sect;2.7's provider interchangeability is unchanged: pointing both agents at another
 * provider is a change to that one bean and a value in {@code application.yml}.
 *
 * <p>No property is new. The briefing reuses {@code freshflow.llm.max-steps}: two mandatory reads,
 * an optional velocity read or two, and the closing answer fit comfortably inside FR14's eight.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(LlmProperties.class)
public class BriefingAgentConfiguration {

    /**
     * FR15's three read-only tools, over the application services the endpoints already use, so
     * that the briefing and {@code GET /api/insight/risks} can never disagree about what is at
     * risk, and the velocity tool is governed by FR12's window-selection ruling with no second
     * copy of that rule.
     */
    @Bean
    public BriefingToolRegistry briefingToolRegistry(RiskQueryService riskQueryService,
                                                     AgentTraceStore agentTraceStore,
                                                     VelocityQueryService velocityQueryService) {
        return new BriefingToolRegistry(riskQueryService, agentTraceStore, velocityQueryService);
    }

    /**
     * The half of FR15 that needs a credential, loaded only when one is present and never under
     * the test profile. See the class javadoc for why {@code @Profile} is repeated here.
     */
    @Configuration
    @Profile("!test")
    @ConditionalOnExpression("'${freshflow.llm.api-key:}'.trim().length() > 0")
    static class LlmBackedBriefingConfiguration {

        @Bean
        public BriefingAgent briefingAgent(ChatModel chatModel, Clock clock,
                                           LlmProperties properties) {
            return new LangChain4jBriefingAgent(chatModel, clock, properties.getMaxSteps());
        }

        /**
         * FR15's use case. Plain constructor arguments, exactly as every orchestration test builds
         * one, so that what runs here is the class the tests assert and not a variant of it.
         */
        @Bean
        public BriefingOrchestrator briefingOrchestrator(BriefingAgent agent,
                                                         BriefingToolRegistry tools,
                                                         BriefingStore briefingStore,
                                                         RiskQueryService riskQueryService,
                                                         AgentTraceStore agentTraceStore,
                                                         Clock clock) {
            return new BriefingOrchestrator(agent, tools, briefingStore, riskQueryService,
                    agentTraceStore, clock);
        }
    }
}
