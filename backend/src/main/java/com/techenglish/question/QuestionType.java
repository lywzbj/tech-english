package com.techenglish.question;

import java.util.Locale;
import java.util.Set;

/**
 * Question types across every domain. Stored as text on {@link Question#getType()};
 * the grammar generator only emits MCQ/TRANSFORM/TRANSLATE/PRODUCE, the reading
 * seeder only MCQ/TRUE_FALSE today, and the AI judge grades the three subjective
 * (typed-answer) types while MCQ/TRUE_FALSE compare against an option text.
 */
public enum QuestionType {
    MCQ,
    TRUE_FALSE,
    HEADING,
    SHORT_ANSWER,
    TRANSFORM,
    TRANSLATE,
    PRODUCE;

    public static final Set<String> SUBJECTIVE = Set.of("TRANSFORM", "TRANSLATE", "PRODUCE");

    public static boolean isSubjective(String type) {
        return type != null && SUBJECTIVE.contains(type.trim().toUpperCase(Locale.ROOT));
    }
}
