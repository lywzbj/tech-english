package com.techenglish.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiGateway {

    private static final String PROMPT_VERSION = "explain-v1";

    private final AiProperties properties;
    private final MockAiProvider mockAiProvider;
    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final ObjectMapper objectMapper;
    private final AiCallLogRepository aiCallLogRepository;

    public AiDtos.AiStatusResponse status() {
        String provider = selectedProvider().name();
        boolean configured = !"deepseek".equals(provider)
                || (properties.getApiKey() != null && !properties.getApiKey().isBlank());
        return new AiDtos.AiStatusResponse(provider, configured, properties.getModel());
    }

    public AiDtos.ExplainResponse explain(Long userId, AiDtos.ExplainRequest request) {
        AiProvider provider = selectedProvider();
        String mode = request.mode() == null || request.mode().isBlank() ? "sentence" : request.mode();
        String systemPrompt = loadPrompt();
        String userPrompt = """
                Mode: %s
                Selected text:
                <learner_text>
                %s
                </learner_text>
                Context from the article:
                <context>
                %s
                </context>
                """.formatted(mode, request.text(), request.context() == null ? "" : request.context());

        long started = System.currentTimeMillis();
        try {
            String raw = provider.chat(systemPrompt, userPrompt, 0.2, properties.getMaxOutputTokens());
            AiDtos.ExplainResponse response = parse(raw, provider);
            log(userId, provider, "SUCCESS", (int) (System.currentTimeMillis() - started), null);
            return response;
        } catch (RuntimeException ex) {
            log(userId, provider, "FAILED", (int) (System.currentTimeMillis() - started), ex.getMessage());
            if (ex instanceof ApiException apiException) {
                throw apiException;
            }
            throw ApiException.unavailable("AI 服务暂时不可用，请稍后重试");
        }
    }

    private AiProvider selectedProvider() {
        if ("deepseek".equalsIgnoreCase(properties.getProvider())
                || "openai".equalsIgnoreCase(properties.getProvider())) {
            return openAiCompatibleProvider;
        }
        return mockAiProvider;
    }

    private String loadPrompt() {
        try {
            return new ClassPathResource("prompts/explain/v1.md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "You are an experienced English teacher. Explain the learner's text in Chinese and return JSON.";
        }
    }

    private AiDtos.ExplainResponse parse(String raw, AiProvider provider) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                cleaned = cleaned.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            String translation = root.path("translation").asText("");
            String explanation = root.path("explanation").asText("");
            List<AiDtos.GrammarPoint> points = objectMapper.convertValue(
                    root.path("grammarPoints"), new TypeReference<>() {
                    });
            List<AiDtos.VocabHint> vocabulary = objectMapper.convertValue(
                    root.path("vocabulary"), new TypeReference<>() {
                    });
            return new AiDtos.ExplainResponse(
                    provider.name(),
                    properties.getModel(),
                    translation,
                    explanation.isBlank() ? cleaned : explanation,
                    points == null ? List.of() : points,
                    vocabulary == null ? List.of() : vocabulary
            );
        } catch (Exception ex) {
            return new AiDtos.ExplainResponse(
                    provider.name(),
                    properties.getModel(),
                    "",
                    cleaned,
                    List.of(),
                    List.of()
            );
        }
    }

    private void log(Long userId, AiProvider provider, String status, int latencyMs, String error) {
        try {
            AiCallLog log = new AiCallLog();
            log.setUserId(userId);
            log.setPurpose("EXPLAIN");
            log.setProvider(provider.name());
            log.setModel(properties.getModel());
            log.setPromptVersion(PROMPT_VERSION);
            log.setLatencyMs(latencyMs);
            log.setStatus(status);
            log.setErrorMessage(error == null ? null : error.substring(0, Math.min(error.length(), 1000)));
            aiCallLogRepository.save(log);
        } catch (Exception ignored) {
            // Logging must never break the learner's request.
        }
    }
}
