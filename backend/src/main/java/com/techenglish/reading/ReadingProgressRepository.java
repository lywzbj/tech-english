package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    Optional<ReadingProgress> findByUserIdAndArticleId(Long userId, Long articleId);
}
