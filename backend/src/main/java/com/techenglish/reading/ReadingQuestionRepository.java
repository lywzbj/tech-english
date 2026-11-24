package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingQuestionRepository extends JpaRepository<ReadingQuestion, Long> {
    List<ReadingQuestion> findByArticleIdOrderBySortOrderAsc(Long articleId);

    long countByArticleId(Long articleId);
}
