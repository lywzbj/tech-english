package com.techenglish.ai;

import java.util.List;

/**
 * DTOs for the grammar exercise generator (语法练习出题). Mirrors the JSON
 * shape of prompts/exercise-gen/v1-*.md. Answers and per-option notes are
 * validated and stored server-side by GrammarService; they never ride along
 * with the lesson payload.
 */
public final class GrammarGenDtos {

    private GrammarGenDtos() {
    }

    /**
     * One generated question. {@code answer} is the full text of the correct
     * option; {@code optionNotes} is aligned with {@code options} (one Chinese
     * note per option). The pool is chosen by the caller, not the model.
     */
    public record GenExercise(
            Integer level,
            String type,
            String skill,
            String prompt,
            List<String> options,
            String answer,
            String explanation,
            List<String> optionNotes
    ) {
    }

    /** Spring AI structured-output target for the exercise-gen purpose. */
    public record ExerciseGenPayload(List<GenExercise> exercises) {
    }

    /** Validated model output plus provenance, straight from the gateway. */
    public record ExerciseGenResponse(
            String provider,
            String model,
            String promptVersion,
            List<GenExercise> exercises
    ) {
    }
}
