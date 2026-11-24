package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrammarLessonRepository extends JpaRepository<GrammarLesson, Long> {
    Optional<GrammarLesson> findByTopicId(Long topicId);
}
