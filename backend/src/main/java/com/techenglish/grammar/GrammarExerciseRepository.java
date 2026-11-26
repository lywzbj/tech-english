package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrammarExerciseRepository extends JpaRepository<GrammarExercise, Long> {

    List<GrammarExercise> findByTopicIdAndDeletedOrderByIdAsc(Long topicId, Integer deleted);

    long countByTopicIdAndDeletedAndPool(Long topicId, Integer deleted, String pool);
}
