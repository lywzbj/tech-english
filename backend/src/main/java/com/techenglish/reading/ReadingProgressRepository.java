package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    Optional<ReadingProgress> findByUserIdAndArticleId(Long userId, Long articleId);

    List<ReadingProgress> findByUserId(Long userId);
}
