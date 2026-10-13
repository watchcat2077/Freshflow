package com.freshflow.insight.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR14's determinism setting. A demonstration must give the same answer for the same facts, so the
 * sampling temperature is fixed at 0.0 by default and is a configuration value
 * ({@code freshflow.llm.temperature}), not a constant inside the adapter.
 *
 * <p>Nothing here builds a model (ADR 0004, R10.1). The binding is exercised with Spring Boot's
 * {@code Binder} alone, and {@code application.yml} is read as a document, so the test is green with
 * or without {@code LLM_API_KEY} in the environment and never touches the network.
 */
@DisplayName("freshflow.llm.temperature: 0.0 by default, bound from configuration")
class LlmPropertiesTest {

    @Test
    @DisplayName("a fresh LlmProperties has temperature 0.0")
    void defaultTemperatureIsZero() {
        assertThat(new LlmProperties().getTemperature()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a configured temperature is bound onto the property")
    void configuredTemperatureIsBound() {
        Binder binder = new Binder(new MapConfigurationPropertySource(
                Map.of("freshflow.llm.temperature", "0.7")));

        LlmProperties bound = binder.bind("freshflow.llm", LlmProperties.class).get();

        assertThat(bound.getTemperature()).isEqualTo(0.7);
    }

    /**
     * The runtime {@code application.yml}, read as a document rather than through a context, so
     * that the demonstration defaults are pinned without building any bean. The api-key placeholder
     * keeps {@code LLM_API_KEY} first and falls back to {@code GEMINI_API_KEY}, so an environment
     * that already carries the provider's own variable works unchanged (spec-01 s2.7: keys come from
     * the environment only).
     */
    @Test
    @DisplayName("application.yml declares temperature 0.0 and falls back to GEMINI_API_KEY for the key")
    void applicationYmlDeclaresTheDemonstrationDefaults() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("freshflow.llm.temperature")).isEqualTo("0.0");
        assertThat(properties.getProperty("freshflow.llm.api-key"))
                .isEqualTo("${LLM_API_KEY:${GEMINI_API_KEY:}}");
    }
}
