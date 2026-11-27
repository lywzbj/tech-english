package com.techenglish.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Active prompt version per purpose. PromptRegistry loads
     * {@code prompts/<purpose>/<version>-system.md} and
     * {@code prompts/<purpose>/<version>-user.md} from the classpath at startup;
     * a missing or blank file fails application startup (fail-fast).
     */
    private Map<String, String> prompts = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("explain", "v2"),           // 阅读讲解
            Map.entry("grammar-explain", "v1"),   // 语法知识解读
            Map.entry("writing-review", "v1"),    // 写作批改
            Map.entry("question-gen", "v2"),      // 阅读理解 AI 自测出题（小节级单选）
            Map.entry("exercise-gen", "v2"),      // 语法练习出题(选择+改写/翻译/造句混合)
            Map.entry("exercise-judge", "v1"),   // 语法输出题 AI 判分
            Map.entry("ability-assess", "v1"),    // 能力评估与学习建议
            Map.entry("preview", "v1"),          // 阅读预习(核心词+结构预告)
            Map.entry("vocab-judge", "v1"),      // 读后结算:猜词判定
            Map.entry("vocab-scan", "v1")));      // 读后结算:漏网高价值词扫描

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
