package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MockAiProvider implements AiProvider {

    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("translation", "演示模式：配置 AI_API_KEY 后可获得完整的上下文翻译。");
        result.put("explanation", "先找主语和谓语，再处理从句和修饰成分。技术英语里常见的难点是长名词短语、被动语态和条件句；拆成短句后再理解会更稳。");
        result.put("grammarPoints", List.of(
                Map.of("point", "句子主干", "detail", "先定位谁做什么，再把时间、条件、原因等修饰成分挂回去。"),
                Map.of("point", "技术语境", "detail", "遇到 API、deployment、latency 这类词，先结合上下文判断它是名词还是动作。")
        ));
        result.put("vocabulary", List.of(
                Map.of("word", "context", "meaning", "上下文；语境", "note", "技术文档中常指运行环境或背景信息。"),
                Map.of("word", "component", "meaning", "组件", "note", "前后端和架构描述中的高频词。")
        ));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build mock AI response", ex);
        }
    }
}
