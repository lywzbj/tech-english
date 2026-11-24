package com.techenglish.identity;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learner_profile")
public class LearnerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "target_band", precision = 3, scale = 1)
    private BigDecimal targetBand;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "daily_minutes", nullable = false)
    private int dailyMinutes = 75;

    @Column(name = "cefr_level", nullable = false, length = 10)
    private String cefrLevel = "A2";

    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private String techStack;

    @Column(columnDefinition = "TEXT")
    private String interests;

    @Column(name = "explanation_language", nullable = false, length = 20)
    private String explanationLanguage = "zh-CN";

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;
}
