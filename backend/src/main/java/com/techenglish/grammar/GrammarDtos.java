package com.techenglish.grammar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
            int sortOrder,
            /** When a mastered topic suggests a review pass; null until the first boss pass. */
            Instant nextReviewAt,
            /** SEED / USER / AI_PROPOSED / FROM_READING */
            String source
    ) {
    }

    /** One freshly drawn boss paper (rotated away from the last submitted set). */
    public record BossDrawResponse(List<ExerciseDto> exercises, String bossSource) {
    }

    /** Full knowledge tree for visualisation: nodes carry mastery, edges carry dependencies. */
    public record TreeResponse(List<TreeNodeDto> nodes, List<TreeEdgeDto> edges, double overallMastery) {
    }

    public record TreeNodeDto(
            Long id,
            String slug,
            String title,
            String world,
            int worldOrder,
            int sortOrder,
            String cefrLevel,
            String summary,
            /** SEED / USER / AI_PROPOSED / FROM_READING */
            String source,
            String status,
            double mastery,
            boolean locked,
            boolean completed,
            Instant nextReviewAt,
            Instant lastPracticedAt,
            Long originArticleId,
            String originSentence
    ) {
    }

    /** up depends on down (down is the prerequisite / parent node). */
    public record TreeEdgeDto(String upSlug, String downSlug, String edgeType) {
    }

    /** Manual addition of a knowledge point (the 新增知识点 form). */
    public record TopicCreateRequest(
            @NotBlank @Size(min = 3, max = 60)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                    message = "标识符只允许小写字母、数字和单个连字符")
            String slug,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 500) String summary,
            @Size(max = 10) String cefrLevel,
            @Size(max = 80) String world,
            Integer worldOrder,
            @Size(max = 6) List<@Size(max = 80) String> prerequisites,
            @Size(max = 20000) String contentMd,
            List<ExampleDto> examples
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
            String source,
            /** Boss paper only: the user has failed this question before and it was
             *  pulled back in for wrong-answer re-practice. */
            boolean wrongReview
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
            HistoryDto history,
            /** Proficiency signal: mastered, or every practice-pool question has been
             *  answered correctly at least once — the fixed practice set adds little,
             *  so the UI should take the user straight to the boss. */
            boolean recommendBoss
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
