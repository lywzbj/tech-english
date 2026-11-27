package com.techenglish.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRegistryTest {

    private final PromptRegistry registry = new PromptRegistry(new AiProperties());

    @Test
    void loadsRegisteredPromptsIntoMemoryAtConstruction() {
        assertThat(registry.purposes()).contains("explain");
        assertThat(registry.systemPrompt("explain"))
                .isNotBlank()
                .contains("senior English teacher");
        assertThat(registry.versionLabel("explain")).isEqualTo("explain-v2");
    }

    @Test
    void everyRegisteredPurposeLoadsAndRendersCompletely() {
        // Covers explain, grammar-explain, writing-review, question-gen,
        // exercise-gen, ability-assess and preview: the system prompt must be
        // non-blank and carry the shared teacher persona, and the user template
        // must render to completion when every known placeholder variable is given.
        Map<String, String> allVars = Map.ofEntries(
                Map.entry("mode", "sentence"), Map.entry("text", "sample text"),
                Map.entry("context", "sample context"), Map.entry("point", "虚拟语气"),
                Map.entry("level", "B1"), Map.entry("purpose", "PR description"),
                Map.entry("focus", ""), Map.entry("title", "Sample article"),
                Map.entry("articleType", "Tech Doc"), Map.entry("count", "8"),
                Map.entry("content", "sample article body"), Map.entry("profile", "Java 工程师"),
                Map.entry("stats", "{}"), Map.entry("question", ""),
                Map.entry("existing", ""),
                Map.entry("word", "buffer"), Map.entry("guess", "缓冲"),
                Map.entry("processed", "latency"), Map.entry("items", "[]"),
                Map.entry("techStack", "Java, backend APIs"), Map.entry("difficulty", "B1"),
                Map.entry("sectionTitle", "How caching works"),
                Map.entry("sectionContent", "Caching keeps hot objects in memory."));

        assertThat(registry.purposes())
                .containsExactlyInAnyOrder("explain", "grammar-explain", "writing-review",
                        "question-gen", "exercise-gen", "exercise-judge", "ability-assess",
                        "preview", "vocab-judge", "vocab-scan");

        for (String purpose : registry.purposes()) {
            assertThat(registry.systemPrompt(purpose))
                    .as("system prompt of " + purpose)
                    .isNotBlank()
                    .contains("senior English teacher");
            assertThat(registry.versionLabel(purpose))
                    .as("version label of " + purpose)
                    .isEqualTo(purpose + "-v"
                            + (java.util.List.of("explain", "question-gen", "exercise-gen")
                                    .contains(purpose) ? "2" : "1"));
            assertThat(registry.render(purpose, allVars))
                    .as("rendered user prompt of " + purpose)
                    .doesNotContain("{{");
        }
    }

    @Test
    void renderFillsEveryPlaceholder() {
        String rendered = registry.render("explain", Map.of(
                "mode", "word",
                "text", "idempotent",
                "context", "Retry semantics"));

        assertThat(rendered)
                .contains("Mode: word")
                .contains("<learner_text>\nidempotent\n</learner_text>")
                .contains("Retry semantics")
                .doesNotContain("{{");
    }

    @Test
    void renderFailsWhenVariablesAreMissing() {
        assertThatThrownBy(() -> registry.render("explain", Map.of("mode", "sentence")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{{text}}");
    }

    @Test
    void unknownPurposeFails() {
        assertThatThrownBy(() -> registry.systemPrompt("grammar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grammar");
    }

    @Test
    void missingPromptFileFailsConstruction() {
        AiProperties properties = new AiProperties();
        properties.getPrompts().put("explain", "v999");

        assertThatThrownBy(() -> new PromptRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prompts/explain/v999-system.md");
    }

    @Test
    void blankPromptFileFailsConstruction() {
        AiProperties properties = new AiProperties();
        properties.getPrompts().put("blank-prompt-test", "v1");

        // src/test/resources/prompts/blank-prompt-test/v1-system.md is a zero-length
        // file kept on purpose as the fail-fast fixture.
        assertThatThrownBy(() -> new PromptRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }
}
