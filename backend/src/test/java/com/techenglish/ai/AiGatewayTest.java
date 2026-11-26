package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mockModelProducesUsableDemoExplanation() {
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        AiGateway gateway = gateway(new AiProperties(), new MockChatModel(objectMapper), repository);

        AiDtos.ExplainResponse response = gateway.explain(1L, request());

        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.explanation()).isNotBlank();
        assertThat(response.grammarPoints()).hasSize(2);
        assertThat(response.vocabulary()).hasSize(2);
    }

    @Test
    void parsesFencedJsonAndRecordsTokenUsage() {
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        String content = """
                ```json
                {
                  "translation": "数据库会锁定这一行。",
                  "explanation": "情态动词 will 表示预期结果。",
                  "grammarPoints": [{"point": "被动语态", "detail": "be + 过去分词"}],
                  "vocabulary": [{"word": "lock", "meaning": "锁定", "note": "技术语境中的动词"}]
                }
                ```
                """;
        AiGateway gateway = gateway(new AiProperties(), new StubChatModel(content, 321, 123), repository);

        AiDtos.ExplainResponse response = gateway.explain(2L, request());

        assertThat(response.translation()).isEqualTo("数据库会锁定这一行。");
        assertThat(response.explanation()).isEqualTo("情态动词 will 表示预期结果。");
        assertThat(response.grammarPoints()).singleElement()
                .satisfies(point -> assertThat(point.point()).isEqualTo("被动语态"));
        assertThat(response.vocabulary()).singleElement()
                .satisfies(hint -> assertThat(hint.word()).isEqualTo("lock"));

        ArgumentCaptor<AiCallLog> log = ArgumentCaptor.forClass(AiCallLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getValue().getInputTokens()).isEqualTo(321);
        assertThat(log.getValue().getOutputTokens()).isEqualTo(123);
    }

    @Test
    void fallsBackToRawTextWhenJsonIsMalformed() {
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        AiGateway gateway = gateway(new AiProperties(), new StubChatModel("Not JSON at all", 10, 5), repository);

        AiDtos.ExplainResponse response = gateway.explain(3L, request());

        assertThat(response.explanation()).isEqualTo("Not JSON at all");
        assertThat(response.grammarPoints()).isEmpty();
        assertThat(response.vocabulary()).isEmpty();
    }

    @Test
    void replacesTruncatedJsonWithFriendlyMessage() {
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        String truncated = "{\"translation\":\"你为类的成员选择顺序\",\"explanation\":\"第一句的主干是 The order";
        AiGateway gateway = gateway(new AiProperties(),
                new StubChatModel(truncated, 416, 1200, "length"), repository);

        AiDtos.ExplainResponse response = gateway.explain(5L, request());

        assertThat(response.translation()).isEmpty();
        assertThat(response.explanation()).contains("长度上限");
        assertThat(response.grammarPoints()).isEmpty();

        ArgumentCaptor<AiCallLog> log = ArgumentCaptor.forClass(AiCallLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("DEGRADED");
        assertThat(log.getValue().getOutputTokens()).isEqualTo(1200);
        assertThat(log.getValue().getErrorMessage()).contains("finishReason=length");
    }

    @Test
    void rejectsRemoteProviderWithoutApiKey() {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setApiKey("");
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        AiGateway gateway = gateway(properties, new StubChatModel("unused", 0, 0), repository);

        assertThat(gateway.status().provider()).isEqualTo("deepseek");
        assertThat(gateway.status().configured()).isFalse();
        assertThatThrownBy(() -> gateway.explain(4L, request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("AI_API_KEY");

        ArgumentCaptor<AiCallLog> log = ArgumentCaptor.forClass(AiCallLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("FAILED");
    }

    private AiGateway gateway(AiProperties properties, ChatModel model, AiCallLogRepository repository) {
        return new AiGateway(properties, ChatClient.builder(model).build(), new PromptRegistry(properties),
                objectMapper, repository);
    }

    private static AiDtos.ExplainRequest request() {
        return new AiDtos.ExplainRequest("The database will lock the row.", "Transaction isolation", "sentence");
    }

    private static final class StubChatModel implements ChatModel {

        private final String content;
        private final int promptTokens;
        private final int completionTokens;
        private final String finishReason;

        private StubChatModel(String content, int promptTokens, int completionTokens) {
            this(content, promptTokens, completionTokens, "stop");
        }

        private StubChatModel(String content, int promptTokens, int completionTokens, String finishReason) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.finishReason = finishReason;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .model("stub-model")
                    .usage(new DefaultUsage(promptTokens, completionTokens))
                    .build();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content),
                    ChatGenerationMetadata.builder().finishReason(finishReason).build())), metadata);
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }
    }
}
