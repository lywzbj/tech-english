package com.techenglish.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {
    List<StudyTask> findByUserIdAndTaskDateOrderBySortOrderAsc(Long userId, LocalDate taskDate);

    List<StudyTask> findByUserIdAndTaskDateBetweenOrderByTaskDateAscSortOrderAsc(
            Long userId, LocalDate from, LocalDate to);

    Optional<StudyTask> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndTaskDateAndStatus(Long userId, LocalDate taskDate, TaskStatus status);

    long countByUserIdAndTaskDateAndStatusAndType(Long userId, LocalDate taskDate, TaskStatus status, TaskType type);
}
