package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingQuizSetRepository extends JpaRepository<ReadingQuizSet, Long> {

    List<ReadingQuizSet> findTop10ByUserIdAndArticleIdOrderByIdDesc(Long userId, Long articleId);
}
