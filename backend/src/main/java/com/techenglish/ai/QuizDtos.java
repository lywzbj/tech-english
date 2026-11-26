package com.techenglish.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for the in-drawer AI reading self-test (阅读理解 AI 自测).
 * Mirrors the JSON shape of prompts/question-gen/v2-system.md.
 */
public final class QuizDtos {

    private QuizDtos() {
    }

    /** One generated question as the model returns it; server-side validation trims this. */
    public record GenQuestion(
            String type,
            String prompt,
            List<String> options,
            String answer,
            String explanation,
            String skill,
            Integer difficulty
    ) {
    }

    /** Spring AI structured-output target for the question-gen purpose. */
    public record QuestionGenPayload(List<GenQuestion> questions) {
    }

    /** Cleaned questions with provenance, straight from the gateway. */
    public record QuestionGenResponse(
            String provider,
            String model,
            String promptVersion,
            List<GenQuestion> questions
    ) {
    }

    // ---- Endpoints ---------------------------------------------------------

    /** Difficulty is EASY | NORMAL | HARD; the section fields describe what the learner is reading. */
    public record QuizGenerateRequest(
            @NotBlank @Size(max = 10) String difficulty,
            @Size(max = 240) String sectionTitle,
            @Size(max = 20000) String sectionText
    ) {
    }

    /** A question without answer/explanation: safe to show before grading. */
    public record QuizQuestionDto(Long id, String prompt, List<String> options) {
    }

    /**
     * One archived set. Questions are included only while the set is ungraded
     * (so a page refresh can restore an in-progress quiz); graded sets return
     * just the score summary.
     */
    public record QuizSetDto(
            Long id,
            String difficulty,
            String sectionTitle,
            int totalCount,
            boolean submitted,
            Integer correctCount,
            Instant createdAt,
            Instant submittedAt,
            List<QuizQuestionDto> questions
    ) {
    }

    public record QuizGenerateResponse(String provider, String model, String promptVersion, QuizSetDto set) {
    }

    public record QuizListResponse(List<QuizSetDto> sets) {
    }

    public record QuizAnswerDto(
            @NotNull Long questionId,
            @NotBlank @Size(max = 500) String answer
    ) {
    }

    public record QuizSubmitRequest(@Valid @Size(max = 20) List<QuizAnswerDto> answers) {
    }

    /** Per-question grading result; the correct answer and explanation appear only here. */
    public record QuizResultItem(
            Long questionId,
            String prompt,
            List<String> options,
            String yourAnswer,
            String correctAnswer,
            boolean correct,
            String explanation
    ) {
    }

    public record QuizSubmitResponse(
            int total,
            int correct,
            double score,
            String message,
            List<QuizResultItem> items
    ) {
    }
}
