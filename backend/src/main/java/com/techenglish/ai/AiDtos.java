package com.techenglish.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    public record ExplainRequest(
            @NotBlank @Size(max = 4000) String text,
            @Size(max = 8000) String context,
            @Size(max = 20) String mode
    ) {
    }

    public record GrammarPoint(String point, String detail) {
    }

    public record VocabHint(String word, String meaning, String note) {
    }

    /**
     * Mirror of the JSON object requested by prompts/explain/v1.md. Used as the
     * Spring AI structured-output target.
     */
    public record ExplainPayload(
            String translation,
            String explanation,
            List<GrammarPoint> grammarPoints,
            List<VocabHint> vocabulary
    ) {
    }

    public record ExplainResponse(
            String provider,
            String model,
            String translation,
            String explanation,
            List<GrammarPoint> grammarPoints,
            List<VocabHint> vocabulary
    ) {
    }

    public record AiStatusResponse(String provider, boolean configured, String model) {
    }
}
