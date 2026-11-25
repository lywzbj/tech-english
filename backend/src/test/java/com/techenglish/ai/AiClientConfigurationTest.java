package com.techenglish.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.techenglish.common.ApiException;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Exercises the real Spring AI OpenAI client against a local OpenAI-compatible
 * server, without needing an API key or network access.
 */
class AiClientConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        String content = """
                {"translation":"数据库会锁定这一行。","explanation":"情态动词 will 表示预期结果。",
                 "grammarPoints":[],"vocabulary":[]}
                """;
        Map<String, Object> response = Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "created", 1,
                "model", "deepseek-chat",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop"
                )),
                "usage", Map.of("prompt_tokens", 12, "completion_tokens", 34, "total_tokens", 46)
        );

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = responseStatus.get();
            byte[] responseBytes = status == 200
                    ? objectMapper.writeValueAsBytes(response)
                    : "{\"error\":{\"message\":\"invalid api key\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void buildsRemoteModelWithoutApiKeySoStartupDoesNotFail() {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setApiKey("");

        AiClientConfiguration configuration = new AiClientConfiguration();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<ObservationRegistry> observationRegistry = beanFactory.getBeanProvider(ObservationRegistry.class);

        assertThat(configuration.aiChatModel(properties, objectMapper, observationRegistry)).isNotNull();
    }

    @Test
    void callsOpenAiCompatibleEndpointThroughSpringAi() throws IOException {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setModel("deepseek-chat");

        AiClientConfiguration configuration = new AiClientConfiguration();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<ObservationRegistry> observationRegistry = beanFactory.getBeanProvider(ObservationRegistry.class);
        ChatModel model = configuration.aiChatModel(properties, objectMapper, observationRegistry);
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        AiGateway gateway = new AiGateway(properties, ChatClient.builder(model).build(), objectMapper, repository);

        AiDtos.ExplainResponse response = gateway.explain(1L,
                new AiDtos.ExplainRequest("The database will lock the row.", "Transactions", "sentence"));

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.translation()).isEqualTo("数据库会锁定这一行。");
        assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("deepseek-chat");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(3000);
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("messages")).hasSize(2);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("content").asText()).contains("The database will lock the row.");

        ArgumentCaptor<AiCallLog> log = ArgumentCaptor.forClass(AiCallLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getValue().getInputTokens()).isEqualTo(12);
        assertThat(log.getValue().getOutputTokens()).isEqualTo(34);
    }

    @Test
    void mapsProviderAuthErrorsToApiException() {
        responseStatus.set(401);
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setApiKey("bad-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

        AiClientConfiguration configuration = new AiClientConfiguration();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<ObservationRegistry> observationRegistry = beanFactory.getBeanProvider(ObservationRegistry.class);
        ChatModel model = configuration.aiChatModel(properties, objectMapper, observationRegistry);
        AiCallLogRepository repository = mock(AiCallLogRepository.class);
        AiGateway gateway = new AiGateway(properties, ChatClient.builder(model).build(), objectMapper, repository);

        assertThatThrownBy(() -> gateway.explain(1L,
                new AiDtos.ExplainRequest("The database will lock the row.", "Transactions", "sentence")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("鉴权失败");

        ArgumentCaptor<AiCallLog> log = ArgumentCaptor.forClass(AiCallLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("FAILED");
    }
}
