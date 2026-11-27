package com.techenglish.grammar;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Prerequisite edge of the grammar knowledge tree: {@code upTopicId} depends on
 * {@code downTopicId} (down is the prerequisite / parent node). This table is the
 * source of truth for unlocking; grammar_topic.prerequisites is a write-only mirror
 * kept for rollback compatibility.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "grammar_topic_edge",
        uniqueConstraints = @UniqueConstraint(name = "uk_topic_edge", columnNames = {"up_topic_id", "down_topic_id"}))
public class GrammarTopicEdge extends BaseEntity {

    @Column(name = "up_topic_id", nullable = false)
    private Long upTopicId;

    @Column(name = "down_topic_id", nullable = false)
    private Long downTopicId;

    @Column(name = "edge_type", nullable = false, length = 20)
    private String edgeType = "PREREQ";

    public static GrammarTopicEdge prerequisite(Long upTopicId, Long downTopicId) {
        GrammarTopicEdge edge = new GrammarTopicEdge();
        edge.setUpTopicId(upTopicId);
        edge.setDownTopicId(downTopicId);
        return edge;
    }
}
