package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrammarTopicEdgeRepository extends JpaRepository<GrammarTopicEdge, Long> {

    List<GrammarTopicEdge> findByDeleted(int deleted);

    boolean existsByUpTopicIdAndDownTopicIdAndDeleted(Long upTopicId, Long downTopicId, int deleted);
}
