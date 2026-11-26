package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingArticleRepository extends JpaRepository<ReadingArticle, Long> {
    List<ReadingArticle> findAllByOrderBySourceTypeAscIdAsc();

    List<ReadingArticle> findBySourceTypeOrderByIdAsc(ReadingSourceType sourceType);

    long countByTagId(Long tagId);
}
