package com.techenglish.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for the reading preview sheet (核心词 + 结构预告).
 * Mirrors the JSON shape of prompts/preview/v1-system.md.
 */
public final class PreviewDtos {

    private PreviewDtos() {
    }

    public record PreviewWord(
            @NotBlank @Size(max = 64) String word,
            @Size(max = 500) String meaning,
            @Size(max = 500) String note,
            @Size(max = 200) String where
    ) {
    }

    public record PreviewGrammar(
            @NotBlank @Size(max = 100) String structure,
            @Size(max = 200) String where,
            @Size(max = 500) String risk,
            @Size(max = 500) String example
    ) {
    }

    /** Spring AI structured-output target; validation annotations only bite on the save path. */
    public record PreviewPayload(List<PreviewWord> words, List<PreviewGrammar> grammar) {
    }

    /** AI draft shown to the learner before confirmation. Never persisted. */
    public record PreviewGenerateResponse(
            String provider,
            String model,
            String promptVersion,
            List<PreviewWord> words,
            List<PreviewGrammar> grammar
    ) {
    }

    /** Confirmed preview stored per (user, article). */
    public record PreviewContent(
            List<PreviewWord> words,
            List<PreviewGrammar> grammar,
            String promptVersion,
            Instant generatedAt
    ) {
    }

    public record PreviewStateResponse(boolean exists, PreviewContent preview) {
    }

    /**
     * The subset the learner ticked in the preview panel, plus the draft metadata
     * (prompt version / provider / model) echoed back for provenance.
     */
    public record PreviewSaveRequest(
            @Valid @Size(max = 15) List<PreviewWord> words,
            @Valid @Size(max = 3) List<PreviewGrammar> grammar,
            @Size(max = 40) String promptVersion,
            @Size(max = 30) String provider,
            @Size(max = 80) String model
    ) {
    }
}
