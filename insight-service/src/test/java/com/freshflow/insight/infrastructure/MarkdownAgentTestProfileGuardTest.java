package com.freshflow.insight.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0004's team ruling <b>R10.1</b>, asserted rather than asserted-about: <i>no test constructs a
 * model client, from configuration or from an API key.</i>
 *
 * <p>R10.1 rests on {@code @Profile("!test")} over everything in
 * {@link MarkdownAgentConfiguration} that can build one. Until 14 September 2026 nothing checked
 * that it held, and it did not: the class-level {@code @Profile} does <b>not</b> reach the
 * {@code static} nested {@code LlmBackedAgentConfiguration}, which component scanning registers as
 * a candidate in its own right. On a developer machine with {@code LLM_API_KEY} set, the nested
 * class activated inside the test context, the outer class did not, and eight integration tests
 * failed on a missing {@link LlmProperties} bean. R10.2 &mdash; the suite is green with the
 * variables <i>unset</i> &mdash; could not see it, because it only ever ran the environment in
 * which the bug is invisible.
 *
 * <h2>Why this test sets the api-key property itself</h2>
 *
 * <p>Reading the ambient {@code LLM_API_KEY} would reproduce the original fault exactly: a test that
 * passes or fails on what the developer's shell happens to hold, which is the class of defect being
 * fixed. Setting {@code freshflow.llm.api-key} here instead satisfies
 * {@code LlmBackedAgentConfiguration}'s {@code @ConditionalOnExpression} on <b>every</b> machine, so
 * the profile guard is the only thing left standing between the test context and a live model
 * client. Remove {@code @Profile("!test")} from the nested class and this test fails everywhere,
 * not somewhere.
 *
 * <p>The value is not a credential and is never used as one; nothing here reaches the network,
 * because the assertion is precisely that the bean which would do so was never created.
 *
 * <h2>Why by bean name and not by type</h2>
 *
 * <p>Asking for {@code ChatModel.class} would put a {@code dev.langchain4j} import in test sources,
 * and a test that must name the library to prove the library is absent is a poor instrument.
 * {@code containsBean} needs only the name, so this file compiles and means the same thing whatever
 * provider module R2 admits.
 */
@SpringBootTest(properties = "freshflow.llm.api-key=not-a-credential-profile-guard-probe")
@ActiveProfiles("test")
@DisplayName("R10.1: no test context builds an LLM client, even with an API key configured")
class MarkdownAgentTestProfileGuardTest {

    /** The bean {@code LlmBackedAgentConfiguration} declares, named as Spring names it. */
    private static final String CHAT_MODEL_BEAN = "markdownAgentChatModel";

    /**
     * The rest of the group guarded by the same nested class. Named so that a partial fix &mdash;
     * one that stops the model being built but leaves its collaborators wired &mdash; is still a
     * failure.
     */
    private static final String[] LLM_BACKED_BEANS = {
            "markdownDecisionAgent",
            "markdownAgentOrchestrator"};

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the test context contains no markdownAgentChatModel bean")
    void testContext_containsNoChatModelBean() {
        assertThat(context.containsBean(CHAT_MODEL_BEAN))
                .as("ADR 0004 R10.1: %s is the one place a live model client is constructed. This "
                        + "context runs under the test profile with freshflow.llm.api-key set, so "
                        + "its presence means the api-key condition is the only guard in force and "
                        + "the profile guard has been lost -- most likely because a @Configuration "
                        + "was nested inside one carrying @Profile and assumed to inherit it.",
                        CHAT_MODEL_BEAN)
                .isFalse();
    }

    /**
     * The same guarantee for the beans beside it, and the reason the context started at all: had
     * the nested class loaded, the failure would not be this assertion but a refresh error on
     * {@code LlmProperties}, which the outer class's {@code @EnableConfigurationProperties} is
     * excluded from providing under this profile.
     */
    @Test
    @DisplayName("nor any other bean from the LLM-backed nested configuration")
    void testContext_containsNoneOfTheLlmBackedBeans() {
        for (String beanName : LLM_BACKED_BEANS) {
            assertThat(context.containsBean(beanName))
                    .as("ADR 0004 R10.1: %s is declared alongside %s in "
                            + "MarkdownAgentConfiguration.LlmBackedAgentConfiguration. The group "
                            + "stands or falls together, so any one of them appearing in a test "
                            + "context means the nested class was loaded.",
                            beanName, CHAT_MODEL_BEAN)
                    .isFalse();
        }
    }
}
