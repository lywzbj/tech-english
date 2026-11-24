package com.techenglish.vocabulary;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserVocabRepository extends JpaRepository<UserVocab, Long> {
    Optional<UserVocab> findByIdAndUserId(Long id, Long userId);

    Optional<UserVocab> findByUserIdAndVocabId(Long userId, Long vocabId);

    List<UserVocab> findByUserIdOrderByNextReviewAtAsc(Long userId);

    List<UserVocab> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            Long userId, Instant cutoff, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndNextReviewAtLessThanEqual(Long userId, Instant cutoff);

    long countByUserIdAndStatus(Long userId, VocabStatus status);

    @Query("select coalesce(avg(v.easeFactor), 0) from UserVocab v where v.userId = :userId")
    Double averageEase(@Param("userId") Long userId);
}
