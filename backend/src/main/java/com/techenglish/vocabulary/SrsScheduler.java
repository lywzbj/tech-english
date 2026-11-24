package com.techenglish.vocabulary;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class SrsScheduler {

    /**
     * SM-2 with four learner ratings. Keeping this deterministic makes the
     * schedule testable independently from the AI layer.
     */
    public void apply(UserVocab item, int rating) {
        int quality = switch (rating) {
            case 0 -> 2;
            case 1 -> 3;
            case 2 -> 4;
            default -> 5;
        };
        if (quality < 3) {
            item.setRepetitions(0);
            item.setIntervalDays(1);
            item.setLapses(item.getLapses() + 1);
        } else {
            int repetitions = item.getRepetitions();
            if (repetitions == 0) {
                item.setIntervalDays(1);
            } else if (repetitions == 1) {
                item.setIntervalDays(3);
            } else {
                item.setIntervalDays(Math.min(180,
                        (int) Math.round(item.getIntervalDays() * item.getEaseFactor().doubleValue())));
            }
            item.setRepetitions(repetitions + 1);
        }
        double ease = item.getEaseFactor() == null ? 2.5 : item.getEaseFactor().doubleValue();
        ease = ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        ease = Math.max(1.3, Math.min(3.0, ease));
        item.setEaseFactor(BigDecimal.valueOf(ease).setScale(2, RoundingMode.HALF_UP));
    }

    public VocabStatus status(UserVocab item, int rating) {
        if (rating == 0) {
            return VocabStatus.LEARNING;
        }
        if (item.getIntervalDays() >= 30 && item.getRepetitions() >= 4) {
            return VocabStatus.MASTERED;
        }
        return VocabStatus.REVIEW;
    }
}
