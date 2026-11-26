package com.techenglish.reading;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ReadingDtos {

    private ReadingDtos() {
    }

    public record ArticleSummary(
            Long id,
            String title,
            String subtitle,
            String sourceType,
            String summary,
            int wordCount,
            String cefrLevel,
            int estimatedMinutes,
            List<String> topics,
            String sourceUrl,
            boolean read,
            /** Raw resume position 0-100. Completion is reported by read, not by this value. */
            int scrollPercent,
            Long tagId,
            String tagName
    ) {
    }

    public record ArticleListResponse(long total, long readCount, List<ArticleSummary> articles) {
    }

    public record QuestionDto(Long id, String type, String prompt, List<String> options) {
    }

    public record ArticleDetail(
            ArticleSummary article,
            String contentMd,
            List<QuestionDto> questions,
            int scrollPercent
    ) {
    }

    public record ImportArticleRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 240) String subtitle,
            @NotBlank @Size(max = 200000) String contentMd,
            @Size(max = 500) String sourceUrl,
            @Size(max = 20) String sourceType,
            Long tagId
    ) {
    }

    public record ImportUrlRequest(
            @NotBlank @Size(max = 1000) String url,
            @Size(max = 200) String title,
            Long tagId
    ) {
    }

    public record TagDto(
            Long id,
            String name,
            int sortOrder,
            boolean enabled,
            long articleCount
    ) {
    }

    public record TagRequest(
            @NotBlank @Size(max = 40) String name,
            @Min(0) @Max(9999) Integer sortOrder
    ) {
    }

    public record TagStatusRequest(@NotNull Boolean enabled) {
    }

    public record AnswerDto(
            @NotNull Long questionId,
            @NotBlank @Size(max = 2000) String answer
    ) {
    }

    public record CompleteRequest(
            @Min(1) @Max(240) int minutes,
            @Valid @Size(max = 50) List<AnswerDto> answers
    ) {
    }

    public record ProgressRequest(
            @Min(0) @Max(100) int scrollPercent
    ) {
    }

    public record CompleteResponse(
            int total,
            int correct,
            double score,
            String message
    ) {
    }

    /** Missed-word scan: words the learner already processed are excluded server-side. */
    public record VocabScanRequest(@Size(max = 300) List<String> knownWords) {
    }
}
