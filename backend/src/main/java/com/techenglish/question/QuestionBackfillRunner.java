package com.techenglish.question;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.grammar.GrammarLesson;
import com.techenglish.grammar.GrammarLessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-off (idempotent) materialiser for the legacy seed questions.
 *
 * <p>Seed grammar questions used to live only inside {@code grammar_lesson.exercises_json}
 * with per-topic ids ({@code q1..q3}); attempts and {@code last_boss_ids} referenced those
 * bare ids. In the unified bank every question needs a globally unique key, so each seed
 * entry becomes a {@code source=SEED} row keyed {@code seed-<topicId>-<id>}, and every
 * reference to the old bare id is rewritten in the same pass. Re-running is a no-op:
 * insertion is skipped when the key already exists, and the rewrite statements stop
 * matching once applied.
 *
 * <p>Runs after ContentSeeder (which seeds fresh databases directly into {@code question}).
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class QuestionBackfillRunner implements ApplicationRunner {

    /** Schema of grammar_lesson.exercises_json entries. */
    public record SeedExercise(String id, String type, String prompt, List<String> options,
                               String answer, String explanation) {
    }

    private static final String SOURCE_SEED = "SEED";
    private static final String POOL_PRACTICE = "PRACTICE";

    private final GrammarLessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (GrammarLesson lesson : lessonRepository.findAll()) {
            try {
                materialise(lesson);
            } catch (Exception ex) {
                // A broken legacy JSON row must never block boot; surface and continue.
                log.warn("seed-question backfill skipped for lesson {}", lesson.getId(), ex);
            }
        }
    }

    private void materialise(GrammarLesson lesson) throws Exception {
        String json = lesson.getExercisesJson();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return;
        }
        Long topicId = lesson.getTopic().getId();
        List<SeedExercise> seeds = objectMapper.readValue(json, new TypeReference<List<SeedExercise>>() {
        }).stream()
                .filter(seed -> seed != null && seed.id() != null && !seed.id().isBlank())
                .toList();
        if (seeds.isEmpty()) {
            return;
        }

        Set<String> existingKeys = new HashSet<>(questionRepository
                .findByDomainAndTopicIdAndDeletedOrderByIdAsc(Question.DOMAIN_GRAMMAR, topicId, 0).stream()
                .filter(row -> SOURCE_SEED.equals(row.getSource()))
                .map(Question::getQuestionKey)
                .toList());

        for (SeedExercise seed : seeds) {
            String newKey = Question.seedKey(topicId, seed.id());
            if (!existingKeys.contains(newKey)) {
                Question row = new Question();
                row.setQuestionKey(newKey);
                row.setDomain(Question.DOMAIN_GRAMMAR);
                row.setTopicId(topicId);
                row.setType(seed.type() == null ? "MCQ" : seed.type());
                row.setPool(POOL_PRACTICE);
                row.setPrompt(seed.prompt());
                row.setOptionsJson(objectMapper.writeValueAsString(
                        seed.options() == null ? List.of() : seed.options()));
                row.setAnswer(seed.answer() == null ? "" : seed.answer());
                row.setExplanation(seed.explanation() == null ? "" : seed.explanation());
                row.setOptionNotesJson("[]");
                row.setSource(SOURCE_SEED);
                questionRepository.save(row);
                log.info("materialised seed question {} as {}", seed.id(), newKey);
            }
            rewriteReferences(topicId, seed.id(), newKey);
        }
    }

    /** Attempts + boss rotation state may still carry the bare legacy id; both rewrites are idempotent. */
    private void rewriteReferences(Long topicId, String oldKey, String newKey) throws Exception {
        jdbcTemplate.update(
                "UPDATE question_attempt SET question_key = ? WHERE domain = ? AND topic_id = ? AND question_key = ?",
                newKey, Question.DOMAIN_GRAMMAR, topicId, oldKey);
        List<Long> progressIds = jdbcTemplate.query(
                "SELECT id FROM grammar_progress WHERE topic_id = ? AND last_boss_ids IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1), topicId);
        for (Long progressId : progressIds) {
            String raw = jdbcTemplate.queryForObject(
                    "SELECT last_boss_ids FROM grammar_progress WHERE id = ?", String.class, progressId);
            List<String> ids = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            if (!ids.contains(oldKey)) {
                continue;
            }
            List<String> replaced = new ArrayList<>(ids);
            replaced.replaceAll(id -> oldKey.equals(id) ? newKey : id);
            jdbcTemplate.update("UPDATE grammar_progress SET last_boss_ids = ? WHERE id = ?",
                    objectMapper.writeValueAsString(replaced), progressId);
        }
    }

}
