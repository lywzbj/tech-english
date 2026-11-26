package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires the AI gateway to Spring AI. The remote model is built explicitly from
 * {@link AiProperties} instead of the OpenAI starter auto-configuration so the
 * application can still start in mock mode without an API key.
 */
@Configuration(proxyBeanMethods = false)
public class AiClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfiguration.class);

    @Bean
    public ChatModel aiChatModel(AiProperties properties, ObjectMapper objectMapper,
                                 ObjectProvider<ObservationRegistry> observationRegistry) {
        if (!properties.remote()) {
            log.info("AI chat model: offline mock (app.ai.provider={})", properties.getProvider());
            return new MockChatModel(objectMapper);
        }
        // The API key itself is never logged — presence only.
        log.info("AI chat model: provider={} model={} baseUrl={} timeoutSeconds={} apiKeyPresent={}",
                properties.getProvider(), properties.getModel(), normalizeBaseUrl(properties.getBaseUrl()),
                Math.max(1, properties.getTimeoutSeconds()),
                properties.getApiKey() != null && !properties.getApiKey().isBlank());
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi(properties))
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel())
                        .build())
                .retryTemplate(retryTemplate())
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP))
                .build();
    }

    @Bean
    public ChatClient aiChatClient(ChatModel aiChatModel) {
        return ChatClient.builder(aiChatModel).build();
    }

    private static OpenAiApi openAiApi(AiProperties properties) {
        int timeoutSeconds = Math.max(1, properties.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(10, timeoutSeconds)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .apiKey(properties.getApiKey() == null ? "" : properties.getApiKey().trim())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }

    /**
     * Spring AI appends /v1/chat/completions to the base URL. Existing deployments
     * configure AI_BASE_URL with a trailing /v1 (the OpenAI-compatible convention),
     * so strip it before handing the URL to Spring AI.
     */
    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (value.isEmpty()) {
            value = "https://api.deepseek.com";
        }
        return value.endsWith("/v1") ? value.substring(0, value.length() - 3) : value;
    }

    /**
     * The Spring AI default retries up to 10 times with a long backoff, which is far
     * beyond what an interactive reading request can wait for.
     */
    private static RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .fixedBackoff(Duration.ofMillis(500))
                .retryOn(TransientAiException.class)
                .retryOn(ResourceAccessException.class)
                .build();
    }
}
