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
 * Last scroll position inside an article, stored as a 0-100 percentage so the
 * reader can resume where the user stopped. One row per (user, article).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_reading_progress_user_article",
                columnNames = {"user_id", "article_id"}))
public class ReadingProgress extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "scroll_percent", nullable = false)
    private int scrollPercent;
}
