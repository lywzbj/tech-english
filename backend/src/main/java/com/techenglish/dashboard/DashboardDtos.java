package com.techenglish.dashboard;

import com.techenglish.plan.PlanDtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record StatsDto(
            int streakDays,
            int weekMinutes,
            double grammarMastery,
            long dueVocab,
            long totalVocab,
            long masteredVocab,
            long articlesRead,
            long totalArticles,
            long tasksCompleted,
            long tasksTotal
    ) {
    }

    public record SkillDto(String key, String label, double value, String note) {
    }

    public record DashboardResponse(
            String greeting,
            String displayName,
            BigDecimal targetBand,
            LocalDate examDate,
            long daysToExam,
            String cefrLevel,
            StatsDto stats,
            List<PlanDtos.TaskDto> todayTasks,
            PlanDtos.TaskDto nextTask,
            List<SkillDto> skills
    ) {
    }
}
