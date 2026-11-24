package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrammarTopicRepository extends JpaRepository<GrammarTopic, Long> {
    List<GrammarTopic> findByPublishedTrueOrderByWorldOrderAscSortOrderAsc();

    Optional<GrammarTopic> findBySlugAndPublishedTrue(String slug);
}
