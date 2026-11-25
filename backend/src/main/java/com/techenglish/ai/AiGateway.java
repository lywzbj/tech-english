package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
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

    private static final String PROMPT_VERSION = "explain-v2";
    private static final double TEMPERATURE = 0.2;

    private final AiProperties properties;
    private final ChatClient aiChatClient;
    private final StructuredOutputConverter<AiDtos.ExplainPayload> outputConverter;
    private final AiCallLogRepository aiCallLogRepository;

    public AiGateway(AiProperties properties, ChatClient aiChatClient, ObjectMapper objectMapper,
                     AiCallLogRepository aiCallLogRepository) {
        this.properties = properties;
        this.aiChatClient = aiChatClient;
        this.outputConverter = new TolerantExplainConverter(
                new BeanOutputConverter<>(AiDtos.ExplainPayload.class, objectMapper));
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
            // responseEntity() lets Spring AI append the converter's JSON format
            // instructions to the prompt and parse the reply, while still handing
            // back the ChatResponse for token usage and finish reason.
            ResponseEntity<ChatResponse, AiDtos.ExplainPayload> response = aiChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions())
                    .call()
                    .responseEntity(outputConverter);
            ChatResponse chatResponse = response.getResponse();
            String raw = extractContent(chatResponse);
            String finishReason = finishReason(chatResponse);
            AiDtos.ExplainPayload payload = response.getEntity();
            boolean degraded = payload == null && looksLikeJson(raw);
            AiDtos.ExplainResponse result = toResponse(provider, raw, payload,
                    "length".equalsIgnoreCase(finishReason));
            log(userId, provider, degraded ? "DEGRADED" : "SUCCESS",
                    (int) (System.currentTimeMillis() - started),
                    tokens(chatResponse, true), tokens(chatResponse, false),
                    degraded ? "Unparseable model output (finishReason=" + finishReason + ")" : null);
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

    private AiDtos.ExplainResponse toResponse(String provider, String raw, AiDtos.ExplainPayload payload,
                                              boolean truncated) {
        if (payload == null) {
            // A plain-text answer still reaches the learner, but broken JSON does not:
            // it is usually structured output cut off by the token limit and would only
            // show the learner a wall of half-finished JSON.
            String explanation = looksLikeJson(raw)
                    ? (truncated
                        ? "AI 输出达到长度上限，未能生成完整讲解。请缩短选中文本后重试。"
                        : "AI 返回的 JSON 无法解析，请重试或缩短选中文本。")
                    : raw;
            return new AiDtos.ExplainResponse(provider, properties.getModel(), "", explanation, List.of(), List.of());
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

    private static boolean looksLikeJson(String raw) {
        String trimmed = raw.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```");
    }

    private static String finishReason(ChatResponse response) {
        if (response == null || response.getResults().isEmpty()) {
            return null;
        }
        var metadata = response.getResult().getMetadata();
        return metadata == null ? null : metadata.getFinishReason();
    }

    private String providerName() {
        return properties.remote() ? properties.getProvider().toLowerCase(Locale.ROOT) : "mock";
    }

    private String loadPrompt() {
        try {
            return new ClassPathResource("prompts/explain/v2.md")
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

    /**
     * Delegates the prompt format instructions to Spring AI's BeanOutputConverter,
     * but turns an unparseable reply into a null entity instead of an exception.
     * Spring AI still returns the ChatResponse, so the gateway can inspect the raw
     * text and finish reason and degrade gracefully when the output was truncated.
     */
    private static final class TolerantExplainConverter implements StructuredOutputConverter<AiDtos.ExplainPayload> {

        private final BeanOutputConverter<AiDtos.ExplainPayload> delegate;

        private TolerantExplainConverter(BeanOutputConverter<AiDtos.ExplainPayload> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getFormat() {
            return delegate.getFormat();
        }

        @Override
        public AiDtos.ExplainPayload convert(String source) {
            try {
                return delegate.convert(source);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
