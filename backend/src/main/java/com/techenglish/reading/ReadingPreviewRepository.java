package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingPreviewRepository extends JpaRepository<ReadingPreview, Long> {

    Optional<ReadingPreview> findByUserIdAndArticleId(Long userId, Long articleId);
}
