package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.SellThroughRiskDetectedEvent;
import com.freshflow.insight.application.AgentToolRegistry;
import com.freshflow.insight.application.AgentUnavailableException;
import com.freshflow.insight.application.BriefingRequest;
import com.freshflow.insight.application.BriefingToolRegistry;
import com.freshflow.insight.application.InMemoryAgentTraceStore;
import com.freshflow.insight.application.RiskQueryService;
import com.freshflow.insight.application.VelocityQueryService;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two LangChain4j adapters, with a {@link ChatModel} that fails the way a provider fails:
 * a {@link RateLimitException} (Gemini's 429, seen in the live run of 5 October 2026 when the
 * model's daily free quota was spent) and a {@link TimeoutException}. No network, no key.
 *
 * <p>What is asserted is the translation at the port boundary: the provider's exception, which
 * the application layer must not know about, becomes {@link AgentUnavailableException}, which it
 * does, with the cause kept for the log and the reason naming the provider's exception class.
 * The orchestrators, the controller and the FR14 consumer all build on that one promise.
 */
@DisplayName("LangChain4j adapters: a failing provider becomes AgentUnavailableException at the port")
class LangChain4jAdapterUnavailableTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** A model whose every call fails with the given provider exception. */
    private static ChatModel failingWith(RuntimeException failure) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw failure;
            }
        };
    }

    private static VelocityQueryService noVelocity() {
        return new VelocityQueryService((sku, from, to) -> List.of(),
                sku -> OptionalDouble.empty(), CLOCK, 60);
    }

    private static SellThroughRiskDetectedEvent trigger() {
        return new SellThroughRiskDetectedEvent(UUID.randomUUID(), NOW.minusSeconds(30),
                "BAT-0001", "SKU-YOG-1KG", 41, 9, 2.0, 23.0);
    }

    @Test
    @DisplayName("FR14 adapter: a 429 from the provider is reported as the FR14 agent being unavailable")
    void markdownAgent_rateLimited_throwsAgentUnavailable() {
        RateLimitException quota = new RateLimitException("{ \"error\": { \"code\": 429 } }");
        AgentToolRegistry tools = new AgentToolRegistry(batchId -> Optional.empty(), noVelocity());
        LangChain4jMarkdownDecisionAgent agent =
                new LangChain4jMarkdownDecisionAgent(failingWith(quota), CLOCK, 8);

        assertThatThrownBy(() -> agent.deliberate(trigger(), tools))
                .isInstanceOf(AgentUnavailableException.class)
                .hasCause(quota)
                .hasMessageContaining("RateLimitException")
                .hasMessageContaining("429");
    }

    @Test
    @DisplayName("FR15 adapter: a timeout from the provider is reported as the FR15 agent being unavailable")
    void briefingAgent_timedOut_throwsAgentUnavailable() {
        TimeoutException slow = new TimeoutException("read timed out after PT1M");
        BriefingToolRegistry tools = new BriefingToolRegistry(
                new RiskQueryService(List::of), new InMemoryAgentTraceStore(), noVelocity());
        LangChain4jBriefingAgent agent = new LangChain4jBriefingAgent(failingWith(slow), CLOCK, 8);

        assertThatThrownBy(() -> agent.brief(new BriefingRequest(LocalDate.of(2026, 10, 5), NOW), tools))
                .isInstanceOf(AgentUnavailableException.class)
                .hasCause(slow)
                .hasMessageContaining("TimeoutException")
                .hasMessageContaining("timed out");
    }
}
