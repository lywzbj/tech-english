package com.techenglish.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LearningEventRepository extends JpaRepository<LearningEvent, Long> {
    List<LearningEvent> findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(Long userId, Instant from);

    long countByUserId(Long userId);
}
