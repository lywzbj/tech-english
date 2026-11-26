package com.techenglish.reading;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One AI-generated self-test set for a (user, article) pair: the learner picks a
 * difficulty, the model writes single-choice questions about the section they were
 * reading, and every generation is archived rather than overwritten — the learner
 * can drill the same article repeatedly and keep the history. Grading fills
 * correctCount/submittedAt once; answers never leave the server before that.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_quiz_set")
public class ReadingQuizSet extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    /** EASY | NORMAL | HARD, chosen by the learner before generation. */
    @Column(nullable = false, length = 10)
    private String difficulty;

    @Column(name = "section_title", length = 240)
    private String sectionTitle;

    @Column(name = "section_ref", length = 120)
    private String sectionRef;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    /** Null until the learner submits; then the number of correct answers. */
    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 30)
    private String provider;

    @Column(length = 80)
    private String model;
}
