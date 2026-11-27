package com.techenglish.grammar;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Staging area for knowledge points that are not in the live tree yet: AI-proposed
 * extensions and grammar structures discovered while reading. Keeping them out of
 * grammar_topic means map queries never have to filter half-approved nodes; adopting
 * a proposal is what writes the topic + prerequisite edges.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "grammar_topic_proposal")
public class GrammarTopicProposal extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 80)
    private String world;

    @Column(name = "world_order")
    private Integer worldOrder;

    @Column(name = "cefr_level", length = 10)
    private String cefrLevel;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** JSON array of prerequisite slugs. */
    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    @Column(name = "content_md", columnDefinition = "TEXT")
    private String contentMd;

    @Column(name = "examples_json", columnDefinition = "TEXT")
    private String examplesJson;

    /** AI_PROPOSED / FROM_READING. */
    @Column(nullable = false, length = 20)
    private String source;

    /** PENDING / ADOPTED / DISCARDED. */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "origin_article_id")
    private Long originArticleId;

    @Column(name = "origin_sentence", columnDefinition = "TEXT")
    private String originSentence;

    /** Raw model output kept for re-parsing when the proposal shape evolves. */
    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;
}
