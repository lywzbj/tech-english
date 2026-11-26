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
     * Mirror of the JSON object requested by prompts/explain/v2.md. Used as the
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

    // ---- Post-reading vocabulary settlement ------------------------------

    /** One "I guessed this meaning" check from reading: word + learner's guess + article sentence. */
    public record JudgeGuessRequest(
            @NotBlank @Size(max = 64) String word,
            @NotBlank @Size(max = 2000) String guess,
            @Size(max = 4000) String context
    ) {
    }

    /** Mirror of prompts/vocab-judge/v1-system.md JSON shape. */
    public record JudgeGuessPayload(String verdict, String meaning, String reason) {
    }

    /** verdict is normalised to correct | partial | wrong before it reaches the client. */
    public record JudgeGuessResponse(String provider, String model, String verdict, String meaning, String reason) {
    }

    public record MissedWord(
            @NotBlank @Size(max = 64) String word,
            @Size(max = 500) String meaning,
            @Size(max = 500) String reason,
            @Size(max = 500) String sentence
    ) {
    }

    /** Mirror of prompts/vocab-scan/v1-system.md JSON shape. */
    public record VocabScanPayload(List<MissedWord> words) {
    }

    public record VocabScanResponse(String provider, String model, List<MissedWord> words) {
    }
}
