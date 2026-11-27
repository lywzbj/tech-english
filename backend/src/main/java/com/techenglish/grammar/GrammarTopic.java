package com.techenglish.grammar;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "grammar_topic")
public class GrammarTopic extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 80)
    private String world;

    @Column(name = "world_order", nullable = false)
    private int worldOrder;

    @Column(name = "cefr_level", nullable = false, length = 10)
    private String cefrLevel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** Write-only mirror of grammar_topic_edge, kept for rollback. Read unlocking from the edge table. */
    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    /** SEED / USER / AI_PROPOSED / FROM_READING — how this knowledge point entered the tree. */
    @Column(nullable = false, length = 20)
    private String source = "SEED";

    /** For FROM_READING leaves: the article and sentence the learner actually got stuck on. */
    @Column(name = "origin_article_id")
    private Long originArticleId;

    @Column(name = "origin_sentence", columnDefinition = "TEXT")
    private String originSentence;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean published = true;
}
