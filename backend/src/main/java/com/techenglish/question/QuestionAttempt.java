package com.techenglish.question;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only answer ledger: one row per graded answer, for every domain.
 * Whether a question was ever seen, when, how it went, and the wrong-answer
 * count all derive from these rows (see QuestionAttemptRepository).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question_attempt")
public class QuestionAttempt extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String domain;

    /** Snapshot of the question's topic / article at attempt time; null when absent. */
    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "question_key", nullable = false, length = 80)
    private String questionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false)
    private boolean correct;
}
