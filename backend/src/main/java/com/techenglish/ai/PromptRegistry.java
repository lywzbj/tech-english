package com.techenglish.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central store for every LLM prompt in the backend.
 *
 * <p>Prompts live on the classpath under
 * {@code prompts/<purpose>/<version>-system.md} (system-level: fixed persona and
 * rules, no placeholders) and {@code prompts/<purpose>/<version>-user.md}
 * (conversation-level: a template with {@code {{name}}} placeholders). The active
 * version per purpose comes from {@code app.ai.prompts} in configuration, so the
 * version string used in ai_call_log and the file path share one source.
 *
 * <p>Everything is read eagerly in the constructor and served from memory at
 * request time. A missing or blank file throws here, which fails bean creation
 * and therefore startup — a silently degraded prompt (the old v2.md-missing bug)
 * cannot happen anymore.
 */
@Component
public class PromptRegistry {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);

    /** A purpose's active system prompt + user template, already loaded into memory. */
    private record PromptSet(String version, String system, String user) {
    }

    private final Map<String, PromptSet> prompts = new HashMap<>();

    public PromptRegistry(AiProperties properties) {
        properties.getPrompts().forEach((purpose, version) -> {
            String system = read("prompts/" + purpose + "/" + version + "-system.md", purpose, "system");
            String user = read("prompts/" + purpose + "/" + version + "-user.md", purpose, "user");
            prompts.put(purpose, new PromptSet(version, system.strip(), user.strip()));
            log.debug("Prompt loaded into memory: {}={} (system {} chars, user {} chars)",
                    purpose, version, system.strip().length(), user.strip().length());
        });
        String summary = prompts.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().version())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        log.info("PromptRegistry ready: {} prompt sets loaded [{}]", prompts.size(), summary);
    }

    /** Registered purposes, for startup checks and diagnostics. */
    public Set<String> purposes() {
        return Set.copyOf(prompts.keySet());
    }

    public String systemPrompt(String purpose) {
        return require(purpose).system();
    }

    /** Version label for ai_call_log, e.g. {@code explain-v2}. */
    public String versionLabel(String purpose) {
        PromptSet set = require(purpose);
        return purpose + "-" + set.version();
    }

    /**
     * Fills the user template for {@code purpose} with {@code vars} (one entry per
     * {@code {{name}}} placeholder). Throws if any placeholder was left unfilled —
     * a half-rendered prompt must never reach the model.
     */
    public String render(String purpose, Map<String, String> vars) {
        String rendered = require(purpose).user();
        for (Map.Entry<String, String> var : vars.entrySet()) {
            rendered = rendered.replace("{{" + var.getKey() + "}}",
                    var.getValue() == null ? "" : var.getValue());
        }
        Matcher leftover = PLACEHOLDER.matcher(rendered);
        if (leftover.find()) {
            throw new IllegalStateException(
                    "Prompt template for purpose '" + purpose + "' still contains placeholder {{"
                            + leftover.group(1) + "}} after rendering");
        }
        return rendered;
    }

    private PromptSet require(String purpose) {
        PromptSet set = prompts.get(purpose);
        if (set == null) {
            throw new IllegalStateException("No prompt registered for purpose '" + purpose
                    + "'; configure app.ai.prompts." + purpose + " or add files under prompts/" + purpose + "/");
        }
        return set;
    }

    private String read(String path, String purpose, String role) {
        ClassPathResource resource = new ClassPathResource(path);
        String text;
        try {
            text = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Prompt file missing for purpose '" + purpose + "' ("
                    + role + "): classpath:" + path, ex);
        }
        if (text.isBlank()) {
            throw new IllegalStateException("Prompt file is blank for purpose '" + purpose + "' ("
                    + role + "): classpath:" + path);
        }
        return text;
    }
}
