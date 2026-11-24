package com.techenglish.grammar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GrammarProgressRepository extends JpaRepository<GrammarProgress, Long> {
    Optional<GrammarProgress> findByUserIdAndTopicId(Long userId, Long topicId);

    List<GrammarProgress> findByUserId(Long userId);

    long countByUserId(Long userId);

    @Query("select coalesce(avg(p.masteryScore), 0) from GrammarProgress p where p.userId = :userId")
    BigDecimal averageMastery(@Param("userId") Long userId);
}
