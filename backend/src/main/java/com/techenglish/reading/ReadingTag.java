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

/**
 * User-manageable document-type tag shown on reading articles (雅思学术 / 技术文档 / ...).
 * A tag may bind to a legacy {@link ReadingSourceType} so imported/seeded pipelines keep
 * working; tags without a binding are stored as CUSTOM on the article.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_tag")
public class ReadingTag extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private ReadingSourceType sourceType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
