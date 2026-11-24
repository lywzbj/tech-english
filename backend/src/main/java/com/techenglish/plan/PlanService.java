package com.techenglish.plan;

import com.techenglish.common.ApiException;
import com.techenglish.grammar.GrammarDtos;
import com.techenglish.grammar.GrammarService;
import com.techenglish.identity.LearnerProfile;
import com.techenglish.identity.LearnerProfileRepository;
import com.techenglish.reading.ReadingArticle;
import com.techenglish.reading.ReadingArticleRepository;
import com.techenglish.reading.ReadingSessionRepository;
import com.techenglish.vocabulary.UserVocabRepository;
import com.techenglish.vocabulary.VocabularyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanService {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final StudyTaskRepository taskRepository;
    private final LearnerProfileRepository profileRepository;
    private final GrammarService grammarService;
    private final VocabularyService vocabularyService;
    private final UserVocabRepository userVocabRepository;
    private final ReadingArticleRepository articleRepository;
    private final ReadingSessionRepository readingSessionRepository;
    private final LearningEventService learningEventService;

    @Transactional
    public PlanDtos.TodayPlanResponse today(Long userId) {
        LocalDate today = LocalDate.now(APP_ZONE);
        List<StudyTask> tasks = ensureDay(userId, today);
        int total = tasks.stream().mapToInt(StudyTask::getEstimatedMinutes).sum();
        int completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE)
                .mapToInt(StudyTask::getEstimatedMinutes).sum();
        return new PlanDtos.TodayPlanResponse(today, total, completed,
                tasks.stream().map(this::toDto).toList());
    }

    @Transactional
    public PlanDtos.WeekPlanResponse week(Long userId) {
        LocalDate today = LocalDate.now(APP_ZONE);
        ensureDay(userId, today);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        List<StudyTask> tasks = taskRepository
                .findByUserIdAndTaskDateBetweenOrderByTaskDateAscSortOrderAsc(userId, monday, sunday);
        Map<LocalDate, List<StudyTask>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(StudyTask::getTaskDate));
        List<PlanDtos.DayPlan> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            List<StudyTask> dayTasks = grouped.getOrDefault(date, List.of()).stream()
                    .sorted(Comparator.comparingInt(StudyTask::getSortOrder))
                    .toList();
            days.add(new PlanDtos.DayPlan(
                    date,
                    date.equals(today),
                    dayTasks.stream().mapToInt(StudyTask::getEstimatedMinutes).sum(),
                    dayTasks.stream().map(this::toDto).toList()
            ));
        }
        int total = tasks.stream().mapToInt(StudyTask::getEstimatedMinutes).sum();
        int completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE)
                .mapToInt(StudyTask::getEstimatedMinutes).sum();
        return new PlanDtos.WeekPlanResponse(days, total, completed);
    }

    @Transactional
    public List<StudyTask> ensureDay(Long userId, LocalDate date) {
        List<StudyTask> existing = taskRepository.findByUserIdAndTaskDateOrderBySortOrderAsc(userId, date);
        if (!existing.isEmpty()) {
            refreshReviewTaskTitle(userId, existing);
            return existing;
        }
        LearnerProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null || !profile.isOnboardingCompleted()) {
            return List.of();
        }
        vocabularyService.ensureDeck(userId);
        long due = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, Instant.now());
        int dailyMinutes = Math.max(30, profile.getDailyMinutes());
        int reviewMinutes = Math.min(15, Math.max(8, dailyMinutes / 5));
        int grammarMinutes = Math.min(25, Math.max(15, dailyMinutes / 3));
        int readingMinutes = Math.min(20, Math.max(10, dailyMinutes - reviewMinutes - grammarMinutes));

        List<StudyTask> tasks = new ArrayList<>();
        int order = 1;
        if (due > 0) {
            StudyTask review = new StudyTask();
            review.setUserId(userId);
            review.setTaskDate(date);
            review.setType(TaskType.REVIEW);
            review.setTitle("复习 " + due + " 个到期词汇");
            review.setDescription("先清到期队列，再学新内容。遗忘并不可怕，按时重逢才是重点。");
            review.setRefType("REVIEW");
            review.setEstimatedMinutes(reviewMinutes);
            review.setSortOrder(order++);
            tasks.add(review);
        }

        GrammarDtos.TopicDto nextTopic = grammarService.nextTopic(userId).orElse(null);
        if (nextTopic != null) {
            StudyTask grammar = new StudyTask();
            grammar.setUserId(userId);
            grammar.setTaskDate(date);
            grammar.setType(TaskType.GRAMMAR);
            grammar.setTitle("语法闯关：" + nextTopic.title());
            grammar.setDescription(nextTopic.summary());
            grammar.setRefType("GRAMMAR");
            grammar.setRefId(nextTopic.id());
            grammar.setEstimatedMinutes(grammarMinutes);
            grammar.setSortOrder(order++);
            tasks.add(grammar);
        }

        Set<Long> readArticleIds = readingSessionRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(session -> session.getArticleId()).collect(Collectors.toCollection(HashSet::new));
        ReadingArticle article = articleRepository.findAllByOrderBySourceTypeAscIdAsc().stream()
                .filter(item -> !readArticleIds.contains(item.getId()))
                .findFirst()
                .orElse(articleRepository.findAllByOrderBySourceTypeAscIdAsc().stream().findFirst().orElse(null));
        if (article != null) {
            StudyTask reading = new StudyTask();
            reading.setUserId(userId);
            reading.setTaskDate(date);
            reading.setType(TaskType.READING);
            reading.setTitle("阅读：" + article.getTitle());
            reading.setDescription(article.getSummary());
            reading.setRefType("READING");
            reading.setRefId(article.getId());
            reading.setEstimatedMinutes(readingMinutes);
            reading.setSortOrder(order);
            tasks.add(reading);
        }
        if (tasks.isEmpty()) {
            return List.of();
        }
        return taskRepository.saveAll(tasks);
    }

    @Transactional
    public PlanDtos.TaskDto complete(Long userId, Long taskId) {
        StudyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "任务不存在"));
        if (task.getStatus() != TaskStatus.DONE) {
            task.setStatus(TaskStatus.DONE);
            task.setCompletedAt(Instant.now());
            taskRepository.save(task);
            learningEventService.record(userId, "TASK_COMPLETED", "TASK", task.getId(),
                    Map.of("type", task.getType().name(), "minutes", task.getEstimatedMinutes()));
        }
        return toDto(task);
    }

    private PlanDtos.TaskDto toDto(StudyTask task) {
        return new PlanDtos.TaskDto(
                task.getId(),
                task.getTaskDate(),
                task.getType().name(),
                task.getTitle(),
                task.getDescription(),
                task.getRefType(),
                task.getRefId(),
                task.getEstimatedMinutes(),
                task.getStatus().name(),
                task.getSortOrder()
        );
    }

    private void refreshReviewTaskTitle(Long userId, List<StudyTask> tasks) {
        tasks.stream()
                .filter(task -> task.getType() == TaskType.REVIEW && task.getStatus() == TaskStatus.PENDING)
                .findFirst()
                .ifPresent(task -> {
                    long due = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, Instant.now());
                    if (due > 0) {
                        String title = "复习 " + due + " 个到期词汇";
                        if (!title.equals(task.getTitle())) {
                            task.setTitle(title);
                            taskRepository.save(task);
                        }
                    }
                });
    }
}
