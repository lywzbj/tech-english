package com.techenglish.grammar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class GrammarDtos {

    private GrammarDtos() {
    }

    public record GrammarPathResponse(List<WorldDto> worlds, double overallMastery) {
    }

    public record WorldDto(String name, int order, List<TopicDto> topics) {
    }

    public record TopicDto(
            Long id,
            String slug,
            String title,
            String cefrLevel,
            String summary,
            String status,
            double mastery,
            List<String> prerequisites,
            boolean locked,
            boolean completed,
            int sortOrder
    ) {
    }

    public record ExampleDto(String sentence, String translation, String note) {
    }

    public record ExerciseDto(String id, String type, String prompt, List<String> options) {
    }

    public record LessonResponse(
            TopicDto topic,
            String contentMd,
            List<ExampleDto> examples,
            List<ExerciseDto> exercises
    ) {
    }

    public record AttemptAnswer(
            @NotBlank @Size(max = 80) String exerciseId,
            @NotBlank @Size(max = 2000) String answer
    ) {
    }

    public record AttemptsRequest(
            @NotEmpty @Size(max = 50) List<@Valid AttemptAnswer> answers
    ) {
    }

    public record ExerciseResult(
            String exerciseId,
            boolean correct,
            String correctAnswer,
            String explanation
    ) {
    }

    public record AttemptsResponse(
            int total,
            int correct,
            double score,
            double mastery,
            String status,
            boolean unlockedNext,
            String nextSlug,
            List<ExerciseResult> results
    ) {
    }
}
