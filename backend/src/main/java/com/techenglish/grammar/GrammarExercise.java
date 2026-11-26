package com.techenglish.grammar;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One AI-generated grammar question, cached per (topic, pool). Pool is PRACTICE
 * or BOSS; the seed exercises still live in grammar_lesson.exercises_json and
 * are merged into the practice pool at read time.
 *
 * <p>answer / explanation / optionNotesJson never reach the client before
 * grading — the lesson endpoint only serialises prompt + options.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "grammar_exercise")
public class GrammarExercise extends BaseEntity {

    /** Stable public id used by submissions and attempt rows, e.g. {@code ai-<uuid>}. */
    @Column(name = "exercise_key", nullable = false, unique = true, length = 80)
    private String exerciseKey;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(nullable = false, length = 10)
    private String pool;

    @Column(name = "exercise_type", nullable = false, length = 20)
    private String type;

    /** recognize | form | use — shown as a small label above the question. */
    @Column(length = 30)
    private String skill;

    @Column(name = "difficulty_level")
    private Integer level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    /** Full text of the correct option (not a letter), matched after normalisation. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** JSON array aligned with options: one Chinese note per option. */
    @Column(name = "option_notes_json", columnDefinition = "TEXT")
    private String optionNotesJson;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 30)
    private String provider;

    @Column(length = 80)
    private String model;
}
