package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiGateway {

    private static final String PURPOSE_EXPLAIN = "explain";
    private static final String PURPOSE_PREVIEW = "preview";
    private static final String PURPOSE_QUESTION_GEN = "question-gen";
    private static final double TEMPERATURE = 0.2;
    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AiProperties properties;
    private final ChatClient aiChatClient;
    private final PromptRegistry promptRegistry;
    private final StructuredOutputConverter<AiDtos.ExplainPayload> outputConverter;
    private final StructuredOutputConverter<PreviewDtos.PreviewPayload> previewConverter;
    private final StructuredOutputConverter<AiDtos.JudgeGuessPayload> judgeConverter;
    private final StructuredOutputConverter<AiDtos.VocabScanPayload> scanConverter;
    private final StructuredOutputConverter<QuizDtos.QuestionGenPayload> questionGenConverter;
    private final AiCallLogRepository aiCallLogRepository;

    public AiGateway(AiProperties properties, ChatClient aiChatClient, PromptRegistry promptRegistry,
                     ObjectMapper objectMapper, AiCallLogRepository aiCallLogRepository) {
        this.properties = properties;
        this.aiChatClient = aiChatClient;
        this.promptRegistry = promptRegistry;
        this.outputConverter = new TolerantConverter<>(
                new BeanOutputConverter<>(AiDtos.ExplainPayload.class, objectMapper));
        this.previewConverter = new TolerantConverter<>(
                new BeanOutputConverter<>(PreviewDtos.PreviewPayload.class, objectMapper));
        this.judgeConverter = new TolerantConverter<>(
                new BeanOutputConverter<>(AiDtos.JudgeGuessPayload.class, objectMapper));
        this.scanConverter = new TolerantConverter<>(
                new BeanOutputConverter<>(AiDtos.VocabScanPayload.class, objectMapper));
        this.questionGenConverter = new TolerantConverter<>(
                new BeanOutputConverter<>(QuizDtos.QuestionGenPayload.class, objectMapper));
        this.aiCallLogRepository = aiCallLogRepository;
    }

    public AiDtos.AiStatusResponse status() {
        return new AiDtos.AiStatusResponse(providerName(), properties.configured(), properties.getModel());
    }

    public AiDtos.ExplainResponse explain(Long userId, AiDtos.ExplainRequest request) {
        String provider = providerName();
        String mode = request.mode() == null || request.mode().isBlank() ? "sentence" : request.mode();
        String systemPrompt = promptRegistry.systemPrompt(PURPOSE_EXPLAIN);
        String userPrompt = promptRegistry.render(PURPOSE_EXPLAIN, Map.of(
                "mode", mode,
                "text", request.text(),
                "context", request.context() == null ? "" : request.context()));

        log.debug("AI {} request: user={} mode={} textChars={} contextChars={} promptVersion={}",
                PURPOSE_EXPLAIN, userId, mode, request.text().length(),
                request.context() == null ? 0 : request.context().length(),
                promptRegistry.versionLabel(PURPOSE_EXPLAIN));
        // The prompts embed the learner's selected text, so full bodies stay at
        // trace: visible during development, absent from production INFO/WARN logs.
        log.trace("AI {} system prompt:\n{}", PURPOSE_EXPLAIN, systemPrompt);
        log.trace("AI {} user prompt:\n{}", PURPOSE_EXPLAIN, userPrompt);

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
            int latencyMs = (int) (System.currentTimeMillis() - started);
            Integer promptTokens = tokens(chatResponse, true);
            Integer completionTokens = tokens(chatResponse, false);
            log.trace("AI {} raw response ({} chars, finishReason={}): {}",
                    PURPOSE_EXPLAIN, raw.length(), finishReason, raw);
            boolean degraded = payload == null && looksLikeJson(raw);
            if (degraded) {
                log.warn("AI call DEGRADED: provider={} model={} finishReason={} latencyMs={} rawChars={}",
                        provider, properties.getModel(), finishReason, latencyMs, raw.length());
            } else {
                log.info("AI call SUCCESS: provider={} model={} promptVersion={} latencyMs={} promptTokens={} completionTokens={}",
                        provider, properties.getModel(), promptRegistry.versionLabel(PURPOSE_EXPLAIN),
                        latencyMs, promptTokens, completionTokens);
            }
            AiDtos.ExplainResponse result = toResponse(provider, raw, payload,
                    "length".equalsIgnoreCase(finishReason));
            recordCall(userId, PURPOSE_EXPLAIN, provider, degraded ? "DEGRADED" : "SUCCESS", latencyMs,
                    promptTokens, completionTokens,
                    degraded ? "Unparseable model output (finishReason=" + finishReason + ")" : null);
            return result;
        } catch (RuntimeException ex) {
            log.error("AI call FAILED: provider={} model={} latencyMs={}",
                    provider, properties.getModel(), System.currentTimeMillis() - started, ex);
            recordCall(userId, PURPOSE_EXPLAIN, provider, "FAILED", (int) (System.currentTimeMillis() - started),
                    null, null, ex.getMessage());
            throw toApiException(ex);
        }
    }

    /**
     * Runs the preview-sheet extraction for one article. Unlike explain(), a
     * failed JSON parse is a hard error here: the draft is only useful as
     * structured words/grammar, never as raw model text. Nothing is persisted —
     * the learner confirms the draft and the reading service stores the subset.
     */
    public PreviewDtos.PreviewGenerateResponse generatePreview(Long userId, Map<String, String> vars) {
        String provider = providerName();
        String systemPrompt = promptRegistry.systemPrompt(PURPOSE_PREVIEW);
        String userPrompt = promptRegistry.render(PURPOSE_PREVIEW, vars);

        log.debug("AI {} request: user={} article={} contentChars={} promptVersion={}",
                PURPOSE_PREVIEW, userId, vars.get("title"),
                vars.get("content") == null ? 0 : vars.get("content").length(),
                promptRegistry.versionLabel(PURPOSE_PREVIEW));
        // The prompt embeds the whole article, so full bodies stay at trace.
        log.trace("AI {} system prompt:\n{}", PURPOSE_PREVIEW, systemPrompt);
        log.trace("AI {} user prompt:\n{}", PURPOSE_PREVIEW, userPrompt);

        long started = System.currentTimeMillis();
        boolean recorded = false;
        try {
            if (!properties.configured()) {
                throw ApiException.unavailable("未配置 AI_API_KEY / AI_BASE_URL，请在 .env 或环境变量中设置");
            }
            ResponseEntity<ChatResponse, PreviewDtos.PreviewPayload> response = aiChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions())
                    .call()
                    .responseEntity(previewConverter);
            ChatResponse chatResponse = response.getResponse();
            String raw = extractContent(chatResponse);
            String finishReason = finishReason(chatResponse);
            PreviewDtos.PreviewPayload payload = response.getEntity();
            int latencyMs = (int) (System.currentTimeMillis() - started);
            Integer promptTokens = tokens(chatResponse, true);
            Integer completionTokens = tokens(chatResponse, false);
            log.trace("AI {} raw response ({} chars, finishReason={}): {}",
                    PURPOSE_PREVIEW, raw.length(), finishReason, raw);
            if (payload == null) {
                log.warn("AI call DEGRADED: provider={} model={} finishReason={} latencyMs={} rawChars={}",
                        provider, properties.getModel(), finishReason, latencyMs, raw.length());
                recordCall(userId, PURPOSE_PREVIEW, provider, "DEGRADED", latencyMs,
                        promptTokens, completionTokens,
                        "Unparseable model output (finishReason=" + finishReason + ")");
                recorded = true;
                throw ApiException.unavailable("AI 预习提取失败，请重试");
            }
            log.info("AI call SUCCESS: provider={} model={} promptVersion={} latencyMs={} promptTokens={} completionTokens={}",
                    provider, properties.getModel(), promptRegistry.versionLabel(PURPOSE_PREVIEW),
                    latencyMs, promptTokens, completionTokens);
            recordCall(userId, PURPOSE_PREVIEW, provider, "SUCCESS", latencyMs,
                    promptTokens, completionTokens, null);
            recorded = true;
            return new PreviewDtos.PreviewGenerateResponse(
                    provider,
                    properties.getModel(),
                    promptRegistry.versionLabel(PURPOSE_PREVIEW),
                    payload.words() == null ? List.of() : payload.words(),
                    payload.grammar() == null ? List.of() : payload.grammar());
        } catch (RuntimeException ex) {
            if (!recorded) {
                log.error("AI call FAILED: provider={} model={} latencyMs={}",
                        provider, properties.getModel(), System.currentTimeMillis() - started, ex);
                recordCall(userId, PURPOSE_PREVIEW, provider, "FAILED",
                        (int) (System.currentTimeMillis() - started), null, null, ex.getMessage());
            }
            throw toApiException(ex);
        }
    }

    /**
     * Judges one guess from the settlement sheet. The verdict is normalised to
     * correct | partial | wrong so a chatty model can never send an unknown label
     * to the client; anything outside the three values degrades to "partial".
     */
    public AiDtos.JudgeGuessResponse judgeGuess(Long userId, AiDtos.JudgeGuessRequest request) {
        AiDtos.JudgeGuessPayload payload = callStructured("vocab-judge", userId, Map.of(
                "word", request.word(),
                "guess", request.guess(),
                "context", request.context() == null ? "" : request.context()),
                judgeConverter, "AI 判定失败，请重试");
        String verdict = payload.verdict() == null ? "" : payload.verdict().trim().toLowerCase(Locale.ROOT);
        if (!List.of("correct", "partial", "wrong").contains(verdict)) {
            verdict = "partial";
        }
        return new AiDtos.JudgeGuessResponse(providerName(), properties.getModel(), verdict,
                payload.meaning() == null ? "" : payload.meaning(),
                payload.reason() == null ? "" : payload.reason());
    }

    /** Scans one article for high-value words the learner never processed. */
    public AiDtos.VocabScanResponse scanVocabulary(Long userId, Map<String, String> vars) {
        AiDtos.VocabScanPayload payload = callStructured("vocab-scan", userId, vars, scanConverter,
                "AI 漏网词扫描失败，请重试");
        return new AiDtos.VocabScanResponse(providerName(), properties.getModel(),
                payload.words() == null ? List.of() : payload.words());
    }

    /**
     * Generates a self-test MCQ set for the section the learner is reading. Strict
     * like the other structured purposes: half-broken questions must not reach the
     * quiz sheet, so an unparseable reply is a retry error. Output is returned raw;
     * the reading service validates, trims to the requested count and persists it.
     */
    public QuizDtos.QuestionGenResponse generateQuestions(Long userId, Map<String, String> vars) {
        QuizDtos.QuestionGenPayload payload = callStructured(PURPOSE_QUESTION_GEN, userId, vars,
                questionGenConverter, "AI 出题失败，请重试");
        return new QuizDtos.QuestionGenResponse(providerName(), properties.getModel(),
                promptRegistry.versionLabel(PURPOSE_QUESTION_GEN),
                payload.questions() == null ? List.of() : payload.questions());
    }

    /**
     * Shared strict structured-output call for purposes whose whole value is the
     * parsed payload (guess verdicts, missed-word lists). Unlike explain(), a null
     * payload is a hard error here: half-broken verdicts or word lists must never
     * reach the settlement sheet, so the learner gets a clear retry message.
     */
    private <T> T callStructured(String purpose, Long userId, Map<String, String> vars,
                                 StructuredOutputConverter<T> converter, String failMessage) {
        String provider = providerName();
        String systemPrompt = promptRegistry.systemPrompt(purpose);
        String userPrompt = promptRegistry.render(purpose, vars);
        log.debug("AI {} request: user={} promptVersion={}", purpose, userId,
                promptRegistry.versionLabel(purpose));
        // The prompt embeds learner text, so full bodies stay at trace.
        log.trace("AI {} system prompt:\n{}", purpose, systemPrompt);
        log.trace("AI {} user prompt:\n{}", purpose, userPrompt);

        long started = System.currentTimeMillis();
        boolean recorded = false;
        try {
            if (!properties.configured()) {
                throw ApiException.unavailable("未配置 AI_API_KEY / AI_BASE_URL，请在 .env 或环境变量中设置");
            }
            ResponseEntity<ChatResponse, T> response = aiChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(chatOptions())
                    .call()
                    .responseEntity(converter);
            ChatResponse chatResponse = response.getResponse();
            String raw = extractContent(chatResponse);
            T payload = response.getEntity();
            int latencyMs = (int) (System.currentTimeMillis() - started);
            Integer promptTokens = tokens(chatResponse, true);
            Integer completionTokens = tokens(chatResponse, false);
            log.trace("AI {} raw response ({} chars): {}", purpose, raw.length(), raw);
            if (payload == null) {
                log.warn("AI call DEGRADED: provider={} model={} purpose={} latencyMs={} rawChars={}",
                        provider, properties.getModel(), purpose, latencyMs, raw.length());
                recordCall(userId, purpose, provider, "DEGRADED", latencyMs, promptTokens, completionTokens,
                        "Unparseable model output");
                recorded = true;
                throw ApiException.unavailable(failMessage);
            }
            log.info("AI call SUCCESS: provider={} model={} purpose={} promptVersion={} latencyMs={} promptTokens={} completionTokens={}",
                    provider, properties.getModel(), purpose, promptRegistry.versionLabel(purpose),
                    latencyMs, promptTokens, completionTokens);
            recordCall(userId, purpose, provider, "SUCCESS", latencyMs, promptTokens, completionTokens, null);
            recorded = true;
            return payload;
        } catch (RuntimeException ex) {
            if (!recorded) {
                log.error("AI call FAILED: provider={} model={} purpose={} latencyMs={}",
                        provider, properties.getModel(), purpose, System.currentTimeMillis() - started, ex);
                recordCall(userId, purpose, provider, "FAILED",
                        (int) (System.currentTimeMillis() - started), null, null, ex.getMessage());
            }
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

    /** Persists the call to ai_call_log; console logging is separate and level-driven. */
    private void recordCall(Long userId, String purpose, String provider, String status, int latencyMs,
                            Integer inputTokens, Integer outputTokens, String error) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setUserId(userId);
            entry.setPurpose(purpose.toUpperCase(Locale.ROOT));
            entry.setProvider(provider);
            entry.setModel(properties.getModel());
            entry.setPromptVersion(promptRegistry.versionLabel(purpose));
            entry.setInputTokens(inputTokens);
            entry.setOutputTokens(outputTokens);
            entry.setLatencyMs(latencyMs);
            entry.setStatus(status);
            entry.setErrorMessage(error == null ? null : error.substring(0, Math.min(error.length(), 1000)));
            aiCallLogRepository.save(entry);
        } catch (Exception ignored) {
            // Call recording must never break the learner's request.
        }
    }

    /**
     * Delegates the prompt format instructions to Spring AI's BeanOutputConverter,
     * but turns an unparseable reply into a null entity instead of an exception.
     * Spring AI still returns the ChatResponse, so the gateway can inspect the raw
     * text and finish reason and degrade gracefully when the output was truncated.
     */
    private static final class TolerantConverter<T> implements StructuredOutputConverter<T> {

        private final BeanOutputConverter<T> delegate;

        private TolerantConverter(BeanOutputConverter<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getFormat() {
            return delegate.getFormat();
        }

        @Override
        public T convert(String source) {
            try {
                return delegate.convert(source);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
