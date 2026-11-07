package com.kavindu.caresummaryapi.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Runs before the app starts. If no Gemini API key is set, it switches the Spring AI chat model off
 * (spring.ai.model.chat=none). Without this, Spring AI would crash at startup without a key.
 * So the app (patients, login, audit log) still works, and only the AI buttons say "not set up".
 * Registered in META-INF/spring.factories.
 */
public class AiToggle implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String key = env.getProperty("spring.ai.google.genai.api-key", "");
        if (key.isBlank() && !env.containsProperty("spring.ai.model.chat")) {
            env.getPropertySources().addLast(
                    new MapPropertySource("aiToggle", Map.of("spring.ai.model.chat", "none")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;   // after .env and application.properties are loaded
    }
}
