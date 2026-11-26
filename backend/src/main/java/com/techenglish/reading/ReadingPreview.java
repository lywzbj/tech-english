package com.techenglish.reading;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI-generated preview sheet (key words + structural signposts) that the learner
 * confirmed for one article. Stored per (user, article) rather than per article:
 * extraction is personalised, so two learners preview the same text differently.
 * Words/grammar are JSON text columns, read back via ObjectMapper (same pattern
 * as reading_article.topics_json).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_preview",
        uniqueConstraints = @UniqueConstraint(name = "uk_reading_preview_user_article",
                columnNames = {"user_id", "article_id"}))
public class ReadingPreview extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "words_json", columnDefinition = "TEXT")
    private String wordsJson;

    @Column(name = "grammar_json", columnDefinition = "TEXT")
    private String grammarJson;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 30)
    private String provider;

    @Column(length = 80)
    private String model;
}
