package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {
    List<ReadingSession> findByUserIdOrderByStartedAtDesc(Long userId);

    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    long countByUserId(Long userId);

    @Query("select coalesce(sum(s.minutes), 0) from ReadingSession s where s.userId = :userId and s.startedAt >= :from")
    long sumMinutesSince(@Param("userId") Long userId, @Param("from") Instant from);
}
