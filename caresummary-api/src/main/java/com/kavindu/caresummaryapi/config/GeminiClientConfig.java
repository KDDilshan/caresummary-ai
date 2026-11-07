package com.kavindu.caresummaryapi.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Our own Gemini client, with a timeout.
 * The default client waits forever for an answer; when Gemini hangs, the doctor's screen would spin forever.
 * With a timeout, the call fails after N seconds and the UI shows "try again".
 * Spring AI uses this bean instead of making its own. Only created when an API key is set.
 */
@Configuration
@ConditionalOnExpression("!'${spring.ai.google.genai.api-key:}'.isBlank()")
public class GeminiClientConfig {

    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                                    @Value("${app.ai.timeout-seconds:60}") int timeoutSeconds) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(timeoutSeconds * 1000).build())
                .build();
    }
}
