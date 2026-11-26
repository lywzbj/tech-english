package com.techenglish.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingQuizQuestionRepository extends JpaRepository<ReadingQuizQuestion, Long> {

    List<ReadingQuizQuestion> findBySetIdOrderBySortOrderAsc(Long setId);
}
