package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class AiGateway {

    private static final String PROMPT_VERSION = "explain-v1";
    private static final double TEMPERATURE = 0.2;

    private final AiProperties properties;
    private final ChatClient aiChatClient;
    private final BeanOutputConverter<AiDtos.ExplainPayload> outputConverter;
    private final AiCallLogRepository aiCallLogRepository;

    public AiGateway(AiProperties properties, ChatClient aiChatClient, ObjectMapper objectMapper,
                     AiCallLogRepository aiCallLogRepository) {
        this.properties = properties;
        this.aiChatClient = aiChatClient;
        this.outputConverter = new BeanOutputConverter<>(AiDtos.ExplainPayload.class, objectMapper);
        this.aiCallLogRepository = aiCallLogRepository;
    }

    public AiDtos.AiStatusResponse status() {
        return new AiDtos.AiStatusResponse(providerName(), properties.configured(), properties.getModel());
    }

    public AiDtos.ExplainResponse explain(Long userId, AiDtos.ExplainRequest request) {
        String provider = providerName();
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
            if (!properties.configured()) {
                throw ApiException.unavailable("未配置 AI_API_KEY / AI_BASE_URL，请在 .env 或环境变量中设置");
            }
            ChatResponse response = aiChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions())
                    .call()
                    .chatResponse();
            String raw = extractContent(response);
            AiDtos.ExplainResponse result = toResponse(provider, raw);
            log(userId, provider, "SUCCESS", (int) (System.currentTimeMillis() - started),
                    tokens(response, true), tokens(response, false), null);
            return result;
        } catch (RuntimeException ex) {
            log(userId, provider, "FAILED", (int) (System.currentTimeMillis() - started), null, null, ex.getMessage());
            throw toApiException(ex);
        }
    }

    private ChatOptions chatOptions() {
        return OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(TEMPERATURE)
                .maxTokens(properties.getMaxOutputTokens())
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResults().isEmpty()) {
            throw ApiException.unavailable("AI 服务没有返回内容");
        }
        String raw = response.getResult().getOutput().getText();
        if (raw == null || raw.isBlank()) {
            throw ApiException.unavailable("AI 返回内容为空");
        }
        return raw;
    }

    private AiDtos.ExplainResponse toResponse(String provider, String raw) {
        AiDtos.ExplainPayload payload = parse(raw);
        if (payload == null) {
            // Preserve the old degradation: a malformed model answer is shown as text
            // instead of turning the whole reading request into a 503.
            return new AiDtos.ExplainResponse(provider, properties.getModel(), "", raw, List.of(), List.of());
        }
        String translation = payload.translation() == null ? "" : payload.translation();
        String explanation = payload.explanation() == null ? "" : payload.explanation();
        return new AiDtos.ExplainResponse(
                provider,
                properties.getModel(),
                translation,
                explanation.isBlank() ? raw : explanation,
                payload.grammarPoints() == null ? List.of() : payload.grammarPoints(),
                payload.vocabulary() == null ? List.of() : payload.vocabulary()
        );
    }

    private AiDtos.ExplainPayload parse(String raw) {
        try {
            return outputConverter.convert(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String providerName() {
        return properties.remote() ? properties.getProvider().toLowerCase(Locale.ROOT) : "mock";
    }

    private String loadPrompt() {
        try {
            return new ClassPathResource("prompts/explain/v1.md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "You are an experienced English teacher. Explain the learner's text in Chinese and return JSON.";
        }
    }

    private static Integer tokens(ChatResponse response, boolean prompt) {
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        Integer value = prompt ? usage.getPromptTokens() : usage.getCompletionTokens();
        return value == null || value <= 0 ? null : value;
    }

    private ApiException toApiException(RuntimeException ex) {
        if (ex instanceof ApiException apiException) {
            return apiException;
        }
        if (ex instanceof NonTransientAiException) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("401") || message.contains("403") || message.contains("invalid_api_key")) {
                return ApiException.unavailable("AI 服务鉴权失败，请检查 AI_API_KEY");
            }
            if (message.contains("429")) {
                return ApiException.unavailable("AI 服务限流，请稍后重试");
            }
            return ApiException.unavailable("AI 服务返回错误，请稍后重试");
        }
        return ApiException.unavailable("AI 服务暂时不可用，请稍后重试");
    }

    private void log(Long userId, String provider, String status, int latencyMs,
                     Integer inputTokens, Integer outputTokens, String error) {
        try {
            AiCallLog log = new AiCallLog();
            log.setUserId(userId);
            log.setPurpose("EXPLAIN");
            log.setProvider(provider);
            log.setModel(properties.getModel());
            log.setPromptVersion(PROMPT_VERSION);
            log.setInputTokens(inputTokens);
            log.setOutputTokens(outputTokens);
            log.setLatencyMs(latencyMs);
            log.setStatus(status);
            log.setErrorMessage(error == null ? null : error.substring(0, Math.min(error.length(), 1000)));
            aiCallLogRepository.save(log);
        } catch (Exception ignored) {
            // Logging must never break the learner's request.
        }
    }
}
