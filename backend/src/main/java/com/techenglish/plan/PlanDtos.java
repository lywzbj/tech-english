package com.techenglish.plan;

import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {

    private PlanDtos() {
    }

    public record TaskDto(
            Long id,
            LocalDate date,
            String type,
            String title,
            String description,
            String refType,
            Long refId,
            int estimatedMinutes,
            String status,
            int sortOrder
    ) {
    }

    public record TodayPlanResponse(
            LocalDate date,
            int totalMinutes,
            int completedMinutes,
            List<TaskDto> tasks
    ) {
    }

    public record DayPlan(LocalDate date, boolean today, int totalMinutes, List<TaskDto> tasks) {
    }

    public record WeekPlanResponse(List<DayPlan> days, int totalMinutes, int completedMinutes) {
    }
}
