package com.techenglish.reading;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_article")
public class ReadingArticle extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 240)
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ReadingSourceType sourceType;

    /** Managed document-type tag (reading_tag.id); null falls back to the sourceType label. */
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "content_md", nullable = false, columnDefinition = "TEXT")
    private String contentMd;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "cefr_level", nullable = false, length = 10)
    private String cefrLevel;

    @Column(name = "topics_json", columnDefinition = "TEXT")
    private String topicsJson;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;
}
