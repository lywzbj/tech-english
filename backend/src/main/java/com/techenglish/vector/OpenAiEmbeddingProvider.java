package com.techenglish.vector;

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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingProvider {

    private final VectorProperties properties;
    private final ObjectMapper objectMapper;

    public boolean configured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public float[] embed(String text) {
        if (!configured()) {
            throw ApiException.unavailable("未配置 EMBEDDING_API_KEY");
        }
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", text);
        if (properties.getModel().startsWith("text-embedding-3")) {
            body.put("dimensions", properties.getDimension());
        }

        try {
            String raw = client.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
            if (raw == null) {
                throw ApiException.unavailable("Embedding 服务没有返回内容");
            }
            JsonNode values = objectMapper.readTree(raw).path("data").path(0).path("embedding");
            if (!values.isArray() || values.isEmpty()) {
                throw ApiException.unavailable("Embedding 返回内容为空");
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            return vector;
        } catch (ApiException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            throw ApiException.unavailable("Embedding 服务返回错误：" + ex.getStatusCode().value());
        } catch (Exception ex) {
            throw ApiException.unavailable("Embedding 服务暂时不可用，请稍后重试");
        }
    }
}
