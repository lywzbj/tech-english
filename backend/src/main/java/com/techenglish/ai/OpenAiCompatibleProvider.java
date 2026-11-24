package com.techenglish.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleProvider implements AiProvider {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw ApiException.unavailable("未配置 AI_API_KEY，请在 .env 或环境变量中设置");
        }
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "response_format", Map.of("type", "json_object")
        );

        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            if (raw == null) {
                throw ApiException.unavailable("AI 服务没有返回内容");
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw ApiException.unavailable("AI 返回内容为空");
            }
            return content.asText();
        } catch (ApiException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            throw ApiException.unavailable("AI 服务返回错误：" + ex.getStatusCode().value());
        } catch (Exception ex) {
            throw ApiException.unavailable("AI 服务暂时不可用，请稍后重试");
        }
    }
}
