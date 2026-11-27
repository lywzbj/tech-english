package com.techenglish.question;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unified question bank: every question in the system (grammar practice/boss,
 * reading static questions, reading self-test items) is one row here, regardless
 * of domain. Provenance lives in dedicated nullable columns — {@code topicId}
 * (grammar knowledge point), {@code articleId} (source article),
 * {@code quizSetId} (which self-test generation produced it).
 *
 * <p>answer / explanation / optionNotesJson never reach the client before
 * grading — endpoints only serialise prompt + options. Whether and how a user
 * answered is tracked separately in {@link QuestionAttempt}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question")
public class Question extends BaseEntity {

    public static final String DOMAIN_GRAMMAR = "GRAMMAR";
    public static final String DOMAIN_READING = "READING";

    /** Key scheme for legacy grammar seed questions materialised from grammar_lesson.exercises_json. */
    public static String seedKey(Long topicId, String legacyId) {
        return "seed-" + topicId + "-" + legacyId;
    }

    /** Stable public id, e.g. {@code ai-<uuid>}, {@code seed-<topicId>-q1}, {@code rq-3}, {@code quiz-<id>}. */
    @Column(name = "question_key", nullable = false, unique = true, length = 80)
    private String questionKey;

    @Column(nullable = false, length = 10)
    private String domain;

    /** 来源知识点（语法题）；阅读题为 null。 */
    @Column(name = "topic_id")
    private Long topicId;

    /** 来源文章（阅读题）；语法题为 null。 */
    @Column(name = "article_id")
    private Long articleId;

    /** 由哪一次自测套卷生成（仅阅读 AI 自测题）。 */
    @Column(name = "quiz_set_id")
    private Long quizSetId;

    /** MCQ / TRANSFORM / TRANSLATE / PRODUCE / TRUE_FALSE / HEADING / SHORT_ANSWER. */
    @Column(name = "question_type", nullable = false, length = 20)
    private String type;

    /** PRACTICE | BOSS — grammar only; null for reading. */
    @Column(length = 10)
    private String pool;

    @Column(length = 30)
    private String skill;

    @Column(name = "difficulty_level")
    private Integer level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    /** JSON array of option texts; {@code []} for open (typed-answer) items. */
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    /** Full text of the correct option, model answer, or TRUE/FALSE word. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** JSON array aligned with options: one Chinese note per option. */
    @Column(name = "option_notes_json", columnDefinition = "TEXT")
    private String optionNotesJson;

    /** SEED | AI — how this row entered the bank. */
    @Column(nullable = false, length = 20)
    private String source;

    /** Reading: which paragraph the evidence sits in, e.g. {@code P4}. */
    @Column(name = "paragraph_ref", length = 40)
    private String paragraphRef;

    /** Reading: display order within the article / quiz set. */
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 30)
    private String provider;

    @Column(length = 80)
    private String model;
}
