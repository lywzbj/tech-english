package com.techenglish.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline demo model used when app.ai.provider is not a remote AI provider.
 * It plugs into Spring AI so the gateway uses the same ChatClient code path
 * with or without an API key.
 */
@RequiredArgsConstructor
public class MockChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(MockChatModel.class);

    private final ObjectMapper objectMapper;

    @Override
    public ChatResponse call(Prompt prompt) {
        // User templates for every structured purpose start with "Task: <name>",
        // so the canned reply can be routed without inspecting prompt bodies.
        String userText = prompt.getInstructions().stream()
                .map(m -> m.getText() == null ? "" : m.getText())
                .filter(t -> t.startsWith("Task: "))
                .findFirst()
                .orElse("");
        String task = userText.isEmpty()
                ? ""
                : userText.substring("Task: ".length()).split("\\R", 2)[0].trim();
        log.debug("MockChatModel: returning canned demo for task '{}' ({} messages)",
                task, prompt.getInstructions().size());
        String json = switch (task) {
            case "article-preview" -> previewJson();
            case "vocab-judge" -> judgeJson();
            case "vocab-scan" -> scanJson();
            case "question-gen" -> questionGenJson();
            default -> responseJson();
        };
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().build();
    }

    /** Canned draft for the preview purpose (prompts/preview, task marker "article-preview"). */
    private String previewJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("words", List.of(
                Map.of("word", "throughput", "meaning", "吞吐量：单位时间内系统处理的请求量",
                        "note", "本文用它衡量优化效果，别按字面理解成“流动量”。", "where", "第1、3段反复出现"),
                Map.of("word", "bottleneck", "meaning", "瓶颈：限制整体性能的那个环节",
                        "note", "常与 identify / remove 搭配。", "where", "第2段主旨句"),
                Map.of("word", "trade-off", "meaning", "权衡：为得到一方而接受另一方的代价",
                        "note", "技术文章高频词，注意复数形式 trade-offs。", "where", "结论段")
        ));
        result.put("grammar", List.of(
                Map.of("structure", "被动语态 + which 非限定从句",
                        "where", "第2段起密集出现",
                        "risk", "which 指代的是前面整件事而不是最近的名词，别把主干找错。",
                        "example", "The cache was invalidated, which doubled the read latency."),
                Map.of("structure", "条件句前置（If/Unless 从句在句首）",
                        "where", "第4段的优化建议部分",
                        "risk", "句子较长，容易在读到主句前丢掉条件关系。",
                        "example", "If the index is missing, the query planner falls back to a full scan.")
        ));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build mock AI preview", ex);
        }
    }

    /** Canned verdict for vocab-judge (task marker "vocab-judge"). */
    private String judgeJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verdict", "partial");
        result.put("meaning", "演示模式：配置 AI_API_KEY 后会给出该词在本文语境中的准确词义。");
        result.put("reason", "本地 mock 模型固定返回“大致正确”，仅用于演示结算判定流程；结算页与批量入库不依赖 AI。");
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build mock AI verdict", ex);
        }
    }

    /** Canned missed-word list for vocab-scan (task marker "vocab-scan"). */
    private String scanJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("words", List.of(
                Map.of("word", "throughput",
                        "meaning", "吞吐量：单位时间内系统处理的请求量",
                        "reason", "全文反复出现，是性能论证的核心词，值得入库",
                        "sentence", "The cache doubled read throughput while keeping tail latency flat."),
                Map.of("word", "commit",
                        "meaning", "提交（事务）：把修改持久化的动作",
                        "reason", "熟词僻义被语境征用——日常义“承诺”，这里是数据库操作",
                        "sentence", "Each writer commits at least once per second under normal load.")
        ));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build mock AI scan", ex);
        }
    }

    /** Canned 5-question MCQ set for question-gen (task marker "question-gen"). */
    private String questionGenJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questions", List.of(
                Map.of("type", "MCQ", "prompt", "What is the main point the section is making?",
                        "options", List.of("A. It argues the current approach should be abandoned.",
                                "B. It explains why a technique matters and what it costs.",
                                "C. It compares two unrelated products.",
                                "D. It lists references for further reading."),
                        "answer", "B", "skill", "main_idea", "difficulty", 1,
                        "explanation", "演示题目：配置真实 AI 服务后，题目会针对你正在读的小节生成。本节围绕某项技术的价值与代价展开。"),
                Map.of("type", "MCQ", "prompt", "According to the section, what happens when the condition is not met?",
                        "options", List.of("A. The system falls back to a slower path.",
                                "B. The request is silently dropped.",
                                "C. The behaviour is unchanged.",
                                "D. The section does not say."),
                        "answer", "A", "skill", "detail", "difficulty", 2,
                        "explanation", "演示题目：细节题考查条件不满足时的降级行为，依据在节内的例子句。"),
                Map.of("type", "MCQ", "prompt", "The word \"trade-off\" in the section is closest in meaning to:",
                        "options", List.of("A. a mistake made under pressure",
                                "B. a balance between gains and losses",
                                "C. a type of software license",
                                "D. a fixed price"),
                        "answer", "B", "skill", "vocabulary", "difficulty", 2,
                        "explanation", "演示题目：词义题。trade-off 指「权衡」，即收益与代价之间的取舍。"),
                Map.of("type", "MCQ", "prompt", "What can be inferred about the author's attitude toward the technique?",
                        "options", List.of("A. Enthusiastic with no reservations.",
                                "B. Cautiously positive, noting real costs.",
                                "C. Openly hostile.",
                                "D. Completely indifferent."),
                        "answer", "B", "skill", "inference", "difficulty", 3,
                        "explanation", "演示题目：推断题。作者既讲收益也摆代价，态度是「谨慎乐观」。"),
                Map.of("type", "MCQ", "prompt", "Which practical consequence does the section imply for a team adopting this?",
                        "options", List.of("A. No monitoring is needed after rollout.",
                                "B. Review the costs before scaling up.",
                                "C. Rewrite the whole system first.",
                                "D. Avoid measuring anything."),
                        "answer", "B", "skill", "design_intent", "difficulty", 3,
                        "explanation", "演示题目：推断实践含义——先核算代价再扩大使用范围。"))
        );
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build mock AI quiz questions", ex);
        }
    }

    private String responseJson() {
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
