package com.techenglish.plan;

import com.techenglish.identity.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping("/today")
    public PlanDtos.TodayPlanResponse today(@AuthenticationPrincipal AuthUser user) {
        return planService.today(user.id());
    }

    @GetMapping("/week")
    public PlanDtos.WeekPlanResponse week(@AuthenticationPrincipal AuthUser user) {
        return planService.week(user.id());
    }

    @PostMapping("/tasks/{id}/complete")
    public PlanDtos.TaskDto complete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return planService.complete(user.id(), id);
    }
}
