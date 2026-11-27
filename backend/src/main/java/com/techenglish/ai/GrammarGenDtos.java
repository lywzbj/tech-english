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
     * One generated question. For {@code MCQ} items, {@code answer} is the full
     * text of the correct option and {@code optionNotes} aligns with
     * {@code options}. For open items ({@code TRANSFORM} / {@code TRANSLATE} /
     * {@code PRODUCE}) both lists are empty and {@code answer} holds one model
     * answer — graded afterwards by the exercise-judge call, not by string match.
     * The pool is chosen by the caller, not the model.
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

    /**
     * One judge verdict for an open (typed-answer) exercise. {@code index} echoes
     * the position of the item in the submitted batch, so verdicts can be matched
     * back to exercises even if the model returns them out of order.
     */
    public record JudgeVerdict(Integer index, Boolean correct, String feedback) {
    }

    /** Spring AI structured-output target for the exercise-judge purpose. */
    public record JudgePayload(List<JudgeVerdict> verdicts) {
    }
}
