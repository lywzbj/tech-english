package com.techenglish.vocabulary;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabReviewRepository extends JpaRepository<VocabReview, Long> {
    long countByUserId(Long userId);
}
