package com.techenglish.reading;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One AI-generated single-choice question inside a {@link ReadingQuizSet}.
 * answer/explanation stay server-side until grading; the grading step writes the
 * learner's choice back into userAnswer/isCorrect, so the row is both the
 * question and its graded result (an attempt per set is the unit of history).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_quiz_question")
public class ReadingQuizQuestion extends BaseEntity {

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    /** Correct option letter A–D. Never sent to the client before grading. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 30)
    private String skill;

    /** Learner's submitted letter; null until grading. */
    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    /** 1/0 after grading, null before. */
    @Column(name = "is_correct")
    private Integer isCorrect;
}
