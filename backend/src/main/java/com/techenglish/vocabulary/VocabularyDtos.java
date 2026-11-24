package com.techenglish.vocabulary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class VocabularyDtos {

    private VocabularyDtos() {
    }

    public record VocabCard(
            Long userVocabId,
            Long vocabId,
            String lemma,
            String pos,
            String phonetic,
            String definitionEn,
            String definitionZh,
            String exampleEn,
            String exampleZh,
            String contextSentence,
            String status,
            int intervalDays,
            String nextReviewAt
    ) {
    }

    public record DueResponse(long dueCount, List<VocabCard> cards) {
    }

    public record ReviewRequest(@Min(0) @Max(3) int rating) {
    }

    public record ReviewResponse(
            Long userVocabId,
            String nextReviewAt,
            int intervalDays,
            String status,
            long remainingDue
    ) {
    }

    public record AddVocabRequest(
            @NotBlank @Size(max = 120) String lemma,
            @Size(max = 1000) String contextSentence
    ) {
    }

    public record LookupResponse(
            Long vocabId,
            String lemma,
            String pos,
            String phonetic,
            String definitionEn,
            String definitionZh,
            String exampleEn,
            String exampleZh,
            String category,
            String domain,
            boolean inDeck
    ) {
    }

    public record VocabularyListResponse(long total, long dueCount, List<VocabCard> words) {
    }
}
