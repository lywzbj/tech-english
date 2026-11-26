package com.techenglish.dashboard;

import com.techenglish.grammar.GrammarService;
import com.techenglish.identity.AppUser;
import com.techenglish.identity.AppUserRepository;
import com.techenglish.identity.LearnerProfile;
import com.techenglish.identity.LearnerProfileRepository;
import com.techenglish.plan.LearningEvent;
import com.techenglish.plan.LearningEventRepository;
import com.techenglish.plan.PlanDtos;
import com.techenglish.plan.PlanService;
import com.techenglish.reading.ReadingArticleRepository;
import com.techenglish.reading.ReadingSession;
import com.techenglish.reading.ReadingSessionRepository;
import com.techenglish.vocabulary.UserVocabRepository;
import com.techenglish.vocabulary.VocabStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppUserRepository userRepository;
    private final LearnerProfileRepository profileRepository;
    private final GrammarService grammarService;
    private final UserVocabRepository userVocabRepository;
    private final ReadingArticleRepository articleRepository;
    private final ReadingSessionRepository readingSessionRepository;
    private final LearningEventRepository learningEventRepository;
    private final PlanService planService;

    @Transactional
    public DashboardDtos.DashboardResponse dashboard(Long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        LearnerProfile profile = profileRepository.findByUserId(userId).orElse(null);
        PlanDtos.TodayPlanResponse todayPlan = planService.today(userId);
        PlanDtos.WeekPlanResponse weekPlan = planService.week(userId);

        long dueVocab = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, Instant.now());
        long totalVocab = userVocabRepository.countByUserId(userId);
        long masteredVocab = userVocabRepository.countByUserIdAndStatus(userId, VocabStatus.MASTERED);
        List<ReadingSession> sessions = readingSessionRepository.findByUserIdOrderByStartedAtDesc(userId);
        Set<Long> readArticleIds = sessions.stream()
                .map(ReadingSession::getArticleId)
                .collect(Collectors.toCollection(HashSet::new));
        long totalArticles = articleRepository.count();
        double grammarValue = grammarService.overallMastery(userId);
        double vocabValue = totalVocab == 0 ? 0 : masteredVocab * 100.0 / totalVocab;
        double readingValue = totalArticles == 0 ? 0 : readArticleIds.size() * 100.0 / totalArticles;
        long tasksCompleted = todayPlan.tasks().stream()
                .filter(task -> "DONE".equals(task.status())).count();
        long tasksTotal = todayPlan.tasks().size();
        PlanDtos.TaskDto nextTask = todayPlan.tasks().stream()
                .filter(task -> !"DONE".equals(task.status()))
                .findFirst().orElse(null);

        LocalDate today = LocalDate.now(PlanService.APP_ZONE);
        long daysToExam = profile == null || profile.getExamDate() == null
                ? 0 : Math.max(0, ChronoUnit.DAYS.between(today, profile.getExamDate()));
        List<DashboardDtos.SkillDto> skills = List.of(
                new DashboardDtos.SkillDto("grammar", "语法", round(grammarValue),
                        grammarValue >= 70 ? "路径掌握度良好" : "先把句子主干练稳"),
                new DashboardDtos.SkillDto("vocabulary", "词汇", round(vocabValue),
                        "已掌握 " + masteredVocab + " / " + totalVocab + " 个"),
                new DashboardDtos.SkillDto("reading", "阅读", round(readingValue),
                        "已读 " + readArticleIds.size() + " / " + totalArticles + " 篇"),
                new DashboardDtos.SkillDto("writing", "写作", 0,
                        "V1.1 开放后接入批改")
        );

        return new DashboardDtos.DashboardResponse(
                greeting(user.getDisplayName()),
                user.getDisplayName(),
                profile == null ? null : profile.getTargetBand(),
                profile == null ? null : profile.getExamDate(),
                daysToExam,
                profile == null ? "A2" : profile.getCefrLevel(),
                new DashboardDtos.StatsDto(
                        streak(userId),
                        weekPlan.completedMinutes(),
                        round(grammarValue),
                        dueVocab,
                        totalVocab,
                        masteredVocab,
                        readArticleIds.size(),
                        totalArticles,
                        tasksCompleted,
                        tasksTotal
                ),
                todayPlan.tasks(),
                nextTask,
                skills
        );
    }

    private int streak(Long userId) {
        Instant from = Instant.now().minus(60, ChronoUnit.DAYS);
        List<LearningEvent> events = learningEventRepository
                .findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(userId, from);
        Set<LocalDate> activeDays = events.stream()
                .map(event -> event.getOccurredAt().atZone(PlanService.APP_ZONE).toLocalDate())
                .collect(Collectors.toSet());
        LocalDate cursor = LocalDate.now(PlanService.APP_ZONE);
        if (!activeDays.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int count = 0;
        while (activeDays.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    private String greeting(String name) {
        int hour = java.time.ZonedDateTime.now(PlanService.APP_ZONE).getHour();
        String part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        return part + ", " + name;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
