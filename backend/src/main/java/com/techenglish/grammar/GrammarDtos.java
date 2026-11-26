package com.techenglish.grammar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    public record ExerciseDto(
            String id,
            String type,
            String prompt,
            List<String> options,
            String skill,
            Integer level,
            String source
    ) {
    }

    /** How many questions each section offers and where the boss pool came from. */
    public record PoolDto(
            int practiceCount,
            int bossCount,
            String bossSource,
            boolean aiConfigured,
            boolean generationNeeded
    ) {
    }

    public record WrongAnswerDto(
            String exerciseId,
            String prompt,
            String yourAnswer,
            String correctAnswer
    ) {
    }

    /** Per-topic attempt summary shown on the lesson page. */
    public record HistoryDto(
            long attempts,
            long correct,
            Instant lastPracticedAt,
            Double bossScore,
            List<WrongAnswerDto> recentWrong
    ) {
    }

    public record LessonResponse(
            TopicDto topic,
            String contentMd,
            List<ExampleDto> examples,
            List<ExerciseDto> practice,
            List<ExerciseDto> boss,
            PoolDto pool,
            HistoryDto history
    ) {
    }

    public record AttemptAnswer(
            @NotBlank @Size(max = 80) String exerciseId,
            @NotBlank @Size(max = 2000) String answer
    ) {
    }

    public record AttemptsRequest(
            /** PRACTICE or BOSS; blank keeps the legacy practice behaviour. */
            @Size(max = 10) String mode,
            @NotEmpty @Size(max = 50) List<@Valid AttemptAnswer> answers
    ) {
    }

    public record ExerciseResult(
            String exerciseId,
            String prompt,
            boolean correct,
            String yourAnswer,
            String correctAnswer,
            String explanation,
            Map<String, String> optionNotes
    ) {
    }

    public record AttemptsResponse(
            String mode,
            int total,
            int correct,
            double score,
            double mastery,
            double bossScore,
            int requiredScore,
            boolean passed,
            String status,
            boolean unlockedNext,
            String nextSlug,
            List<ExerciseResult> results
    ) {
    }

    public record GenerateResponse(
            String provider,
            String model,
            String promptVersion,
            int practiceAdded,
            int bossAdded,
            boolean practiceReady,
            boolean bossReady
    ) {
    }
}
