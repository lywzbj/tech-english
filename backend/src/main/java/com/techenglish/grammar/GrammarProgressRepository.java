package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrammarProgressRepository extends JpaRepository<GrammarProgress, Long> {
    Optional<GrammarProgress> findByUserIdAndTopicId(Long userId, Long topicId);

    List<GrammarProgress> findByUserId(Long userId);

    long countByUserId(Long userId);
}
