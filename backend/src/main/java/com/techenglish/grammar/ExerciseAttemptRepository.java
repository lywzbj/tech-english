package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, Long> {

    List<ExerciseAttempt> findTop30ByUserIdAndTopicIdOrderByIdDesc(Long userId, Long topicId);
}
