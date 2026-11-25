package com.techenglish.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    public static final String PROVIDER_MOCK = "mock";

    /**
     * Free-form provider label. "mock" selects the built-in offline stub; any other
     * value (openai, deepseek, qwen, company-gateway, vllm, one-api, ...) is treated
     * as an OpenAI Chat Completions-compatible service pointed at by baseUrl. Adding a
     * vendor that speaks a different protocol means extending AiClientConfiguration,
     * not changing this label check.
     */
    private String provider = PROVIDER_MOCK;
    private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private int maxOutputTokens = 3000;
    private int timeoutSeconds = 45;

    public boolean remote() {
        return !PROVIDER_MOCK.equalsIgnoreCase(provider);
    }

    public boolean configured() {
        if (!remote()) {
            return true;
        }
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }
}
