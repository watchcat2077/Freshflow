package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import com.freshflow.insight.application.AgentToolRegistry;
import com.freshflow.insight.application.AgentTraceRecord;
import com.freshflow.insight.application.AgentTraceStore;
import com.freshflow.insight.application.AgentUnavailableException;
import com.freshflow.insight.application.BatchDetailsProvider;
import com.freshflow.insight.application.MarkdownAgentOrchestrator;
import com.freshflow.insight.application.MarkdownDecisionAgent;
import com.freshflow.insight.application.MarkdownRecommendationPublisher;
import com.freshflow.insight.application.VelocityQueryService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.function.Consumer;

/**
 * FR14's runtime wiring &mdash; the trigger consumer, the tool registry, the adapters, and the one
 * place a live model is ever constructed.
 *
 * <h2>Two guards, doing two different jobs</h2>
 *
 * <p><b>{@code @Profile("!test")}.</b> Every test in this service runs under
 * {@code @ActiveProfiles("test")}, so nothing declared under this annotation is built by any test
 * context. That is the mechanical form of ADR 0004's promise that no test resolves a live model.
 * It is stated on every {@code @Configuration} class here, the nested one included: a
 * {@code static} nested {@code @Configuration} is picked up by component scanning as a candidate
 * in its own right and does not inherit the enclosing class's {@code @Profile}, so a guard on the
 * outer class alone would let the nested one activate on any machine with {@code LLM_API_KEY} set
 * and then fail to resolve {@link LlmProperties}. {@code MarkdownAgentTestProfileGuardTest}
 * asserts the result by bean name against a test context with the api-key property deliberately
 * set: <b>no test context builds a model client, whatever the developer's environment holds</b>.
 *
 * <p><b>{@code @ConditionalOnExpression} on a non-empty API key.</b> A different job. spec-01
 * &sect;2.7 requires the key to come from an environment variable, so on a machine without one
 * there is nothing to build a model from. The nested class below is then not loaded at all:
 * {@code insight-service} still starts and still serves FR12 and FR13, and FR14 is simply not
 * configured. Building a model with an empty credential instead would defer the same failure to
 * the first risk event, which is a worse place to discover it.
 *
 * <h2>Why the consumer is outside the key guard</h2>
 *
 * <p>{@code spring.cloud.function.definition} names {@code markdownAgent}, and a definition naming
 * a bean that does not exist fails at startup. The consumer is therefore always declared outside
 * the test profile, and reaches the orchestrator through an {@link ObjectProvider}: with no key
 * configured it says so, once per event, and drops the trigger.
 *
 * <h2>Why a consumer function and not a listener</h2>
 *
 * <p>spec-06 &sect;2 (team ruling R6): a second Spring Cloud Stream function on the
 * <b>message-channel</b> binder. It runs on a binder consumer thread, so a model call taking
 * seconds cannot stall a Kafka Streams {@code poll} and trigger a rebalance in the middle of FR12's
 * windowed aggregate. The FR12/FR13 topology's stages, stores and {@code application.id} are
 * untouched, and both binders coexist in this service.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties({LlmProperties.class, InventoryClientProperties.class})
public class MarkdownAgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MarkdownAgentConfiguration.class);

    /**
     * FR14's three tools (R4), over the {@code BatchDetailsProvider} adapter and the existing
     * {@link VelocityQueryService}.
     *
     * <p>The velocity tool reads FR12 through the same service the endpoint does, so FR12's
     * window-selection ruling governs what the agent is told with no second copy of that rule.
     */
    @Bean
    public AgentToolRegistry agentToolRegistry(BatchDetailsProvider batchDetailsProvider,
                                               VelocityQueryService velocityQueryService) {
        return new AgentToolRegistry(batchDetailsProvider, velocityQueryService);
    }

    /** spec-06 &sect;3's ruling: unit cost comes over synchronous REST from {@code inventory-service}. */
    @Bean
    public BatchDetailsProvider batchDetailsProvider(RestClient.Builder builder,
                                                     InventoryClientProperties properties) {
        return new RestBatchDetailsProvider(builder.baseUrl(properties.getBaseUrl()).build());
    }

    /** spec-01 &sect;2.6 Table A: {@code markdown-recommended}, the only channel out of FR14. */
    @Bean
    public MarkdownRecommendationPublisher markdownRecommendationPublisher(
            StreamBridge streamBridge) {
        return new StreamBridgeMarkdownRecommendationPublisher(streamBridge);
    }

    /**
     * spec-06 &sect;2's consumer &mdash; the {@code sell-through-risk-detected} trigger.
     *
     * <p>It calls {@code onRiskDetected} and logs what came back. The method is idempotent and
     * returns the existing trace on a redelivery, so at-least-once delivery needs no handling here
     * beyond calling it (spec-06 &sect;5).
     *
     * <p>An exception from the deliberation is deliberately not swallowed. The binder's retry and
     * error handling are then in force, and the trigger is not acknowledged as decided when it was
     * not decided. The one case this consumer names before rethrowing is the model being
     * unusable ({@link AgentUnavailableException}, from the adapter): a single
     * {@code [fr14-agent-unavailable]} line with the batch, the trigger and the bounded reason,
     * so that an operator reading the console on the day learns "quota" or "timeout" without
     * reading the stack the binder prints afterwards. No trace is written for it: an outage is
     * not a decision (spec-06 &sect;5). Added 5 October 2026 after a live run.
     */
    @Bean
    public Consumer<SellThroughRiskDetectedEvent> markdownAgent(
            ObjectProvider<MarkdownAgentOrchestrator> orchestrators) {

        return trigger -> {
            MarkdownAgentOrchestrator orchestrator = orchestrators.getIfAvailable();
            if (orchestrator == null) {
                log.warn("[fr14-not-configured] batchId={} eventId={} -- no LLM API key is set, "
                                + "so no agent is wired and this trigger was not deliberated",
                        trigger.batchId(), trigger.eventId());
                return;
            }

            AgentTraceRecord trace;
            try {
                trace = orchestrator.onRiskDetected(trigger);
            } catch (AgentUnavailableException modelDown) {
                log.warn("[fr14-agent-unavailable] batchId={} eventId={} reason=\"{}\" -- the trigger "
                                + "was not deliberated and no trace was written; the binder's retry "
                                + "policy decides whether it is tried again",
                        trigger.batchId(), trigger.eventId(), modelDown.reason());
                throw modelDown;
            }
            log.info("[fr14-trace] agentTraceId={} batchId={} finalRecommendation={}",
                    trace.agentTraceId(), trace.batchId(), trace.finalRecommendation());
        };
    }

    /**
     * The half of FR14 that needs a credential, loaded only when one is present and never under the
     * test profile.
     *
     * <p>Nested so that the condition is evaluated once, for a group of beans that stand or fall
     * together, rather than bean by bean in an order Spring does not promise.
     *
     * <p><b>{@code @Profile("!test")} is repeated here and is not redundant.</b> A {@code static}
     * nested {@code @Configuration} is an independent component-scan candidate in its own right, so
     * Spring registers it on its own merits and it does <b>not</b> inherit the enclosing class's
     * {@code @Profile}. Without this line the test profile excluded the outer class &mdash; taking
     * {@code @EnableConfigurationProperties} with it &mdash; while the nested class still activated
     * on any machine where {@code LLM_API_KEY} happened to be set, and then failed to resolve
     * {@link LlmProperties}. Pinned by {@code MarkdownAgentTestProfileGuardTest}.
     */
    @Configuration
    @Profile("!test")
    @ConditionalOnExpression("'${freshflow.llm.api-key:}'.trim().length() > 0")
    static class LlmBackedAgentConfiguration {

        /**
         * The one place in this service where a live model client is constructed.
         *
         * <p>spec-01 &sect;2.7's interchangeability claim is delivered by the
         * {@link MarkdownDecisionAgent} port (ADR 0004), not by a branch here: pointing FreshFlow
         * at a local Ollama is a second adapter behind that port plus a value in
         * {@code application.yml}, and no application-layer code moves. What this method does is
         * build the provider the team chose, from configuration, with the key from the environment.
         *
         * <p>The key is read and handed to the builder. It is never logged, never written to a file
         * and never included in an exception message.
         */
        @Bean
        public ChatModel markdownAgentChatModel(LlmProperties properties) {
            log.info("FR14: LLM provider={} model={} timeout={} maxSteps={} temperature={} (api key "
                            + "supplied from the environment, never logged)",
                    properties.getProvider(), properties.getModel(), properties.getTimeout(),
                    properties.getMaxSteps(), properties.getTemperature());

            return GoogleAiGeminiChatModel.builder()
                    .apiKey(properties.getApiKey())
                    .modelName(properties.getModel())
                    .timeout(properties.getTimeout())
                    // Fixed at 0.0 by default: the same facts must yield the same decision on the day.
                    .temperature(properties.getTemperature())
                    .sendThinking(true)
                    .returnThinking(true)
                    .build();
        }

        @Bean
        public MarkdownDecisionAgent markdownDecisionAgent(ChatModel chatModel, Clock clock,
                                                           LlmProperties properties) {
            return new LangChain4jMarkdownDecisionAgent(chatModel, clock, properties.getMaxSteps());
        }

        /**
         * FR14's use case. Plain constructor arguments, exactly as every orchestration test builds
         * one, so that what runs here is the class the tests assert and not a variant of it.
         */
        @Bean
        public MarkdownAgentOrchestrator markdownAgentOrchestrator(
                MarkdownDecisionAgent agent,
                AgentToolRegistry tools,
                AgentTraceStore traceStore,
                MarkdownRecommendationPublisher publisher,
                BatchDetailsProvider batchDetailsProvider,
                Clock clock) {

            return new MarkdownAgentOrchestrator(agent, tools, traceStore, publisher,
                    batchDetailsProvider, clock);
        }
    }
}
