package com.techenglish.vocabulary;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_vocab",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_vocab_user_word", columnNames = {"user_id", "vocab_id"}))
public class UserVocab extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vocab_id", nullable = false)
    private Long vocabId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VocabStatus status = VocabStatus.NEW;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor = new BigDecimal("2.50");

    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 0;

    @Column(nullable = false)
    private int repetitions = 0;

    @Column(nullable = false)
    private int lapses = 0;

    @Column(name = "next_review_at", nullable = false)
    private Instant nextReviewAt = Instant.now();

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "context_sentence", columnDefinition = "TEXT")
    private String contextSentence;
}
