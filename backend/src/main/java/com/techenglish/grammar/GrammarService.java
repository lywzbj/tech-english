package com.techenglish.grammar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrammarService {

    private final GrammarTopicRepository topicRepository;
    private final GrammarLessonRepository lessonRepository;
    private final GrammarProgressRepository progressRepository;
    private final ExerciseAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public GrammarDtos.GrammarPathResponse path(Long userId) {
        List<GrammarTopic> topics = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc();
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        Map<String, GrammarTopic> bySlug = topics.stream()
                .collect(Collectors.toMap(GrammarTopic::getSlug, Function.identity()));
        List<GrammarDtos.TopicDto> topicDtos = topics.stream()
                .map(topic -> toTopicDto(topic, progress.get(topic.getId()), bySlug, progress))
                .toList();

        Map<String, List<GrammarDtos.TopicDto>> grouped = new LinkedHashMap<>();
        for (GrammarDtos.TopicDto topic : topicDtos) {
            GrammarTopic entity = topics.stream().filter(t -> t.getId().equals(topic.id())).findFirst().orElseThrow();
            grouped.computeIfAbsent(entity.getWorld(), key -> new ArrayList<>()).add(topic);
        }
        List<GrammarDtos.WorldDto> worlds = new ArrayList<>();
        int worldOrder = 1;
        for (Map.Entry<String, List<GrammarDtos.TopicDto>> entry : grouped.entrySet()) {
            worlds.add(new GrammarDtos.WorldDto(entry.getKey(), worldOrder++, entry.getValue()));
        }
        BigDecimal average = progressRepository.averageMastery(userId);
        return new GrammarDtos.GrammarPathResponse(worlds, round(average));
    }

    @Transactional(readOnly = true)
    public GrammarDtos.LessonResponse lesson(Long userId, String slug) {
        GrammarTopic topic = topicRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "语法节点不存在"));
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        Map<String, GrammarTopic> bySlug = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()
                .stream().collect(Collectors.toMap(GrammarTopic::getSlug, Function.identity()));
        GrammarDtos.TopicDto topicDto = toTopicDto(topic, progress.get(topic.getId()), bySlug, progress);
        if (topicDto.locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));
        List<StoredExercise> exercises = readJson(
                lesson.getExercisesJson(), new TypeReference<List<StoredExercise>>() {
                }, List.of());
        return new GrammarDtos.LessonResponse(
                topicDto,
                lesson.getContentMd(),
                readJson(lesson.getExamplesJson(), new TypeReference<List<GrammarDtos.ExampleDto>>() {
                }, List.of()),
                exercises.stream()
                        .map(ex -> new GrammarDtos.ExerciseDto(ex.id(), ex.type(), ex.prompt(), ex.options()))
                        .toList()
        );
    }

    @Transactional
    public GrammarDtos.AttemptsResponse submit(Long userId, String slug, GrammarDtos.AttemptsRequest request) {
        GrammarTopic topic = topicRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "语法节点不存在"));
        Map<Long, GrammarProgress> allProgress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        Map<String, GrammarTopic> bySlug = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()
                .stream().collect(Collectors.toMap(GrammarTopic::getSlug, Function.identity()));
        if (toTopicDto(topic, allProgress.get(topic.getId()), bySlug, allProgress).locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));
        List<StoredExercise> exercises = readJson(
                lesson.getExercisesJson(), new TypeReference<List<StoredExercise>>() {
                }, List.of());
        Map<String, StoredExercise> exerciseMap = exercises.stream()
                .collect(Collectors.toMap(StoredExercise::id, Function.identity()));
        Map<String, String> submitted = request.answers().stream()
                .collect(Collectors.toMap(GrammarDtos.AttemptAnswer::exerciseId,
                        GrammarDtos.AttemptAnswer::answer, (left, right) -> right));

        List<GrammarDtos.ExerciseResult> results = new ArrayList<>();
        int correct = 0;
        for (String exerciseId : submitted.keySet()) {
            StoredExercise exercise = exerciseMap.get(exerciseId);
            if (exercise == null) {
                continue;
            }
            boolean isCorrect = normalize(submitted.get(exerciseId)).equals(normalize(exercise.answer()));
            if (isCorrect) {
                correct++;
            }
            ExerciseAttempt attempt = new ExerciseAttempt();
            attempt.setUserId(userId);
            attempt.setTopicId(topic.getId());
            attempt.setExerciseKey(exerciseId);
            attempt.setAnswer(submitted.get(exerciseId));
            attempt.setCorrect(isCorrect);
            attemptRepository.save(attempt);
            results.add(new GrammarDtos.ExerciseResult(
                    exerciseId, isCorrect, exercise.answer(), exercise.explanation()));
        }
        int total = results.size();
        if (total == 0) {
            throw ApiException.badRequest("NO_VALID_ANSWERS", "没有提交有效的练习题");
        }
        double score = total == 0 ? 0 : correct * 100.0 / total;
        GrammarProgress progress = progressRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> {
                    GrammarProgress created = new GrammarProgress();
                    created.setUserId(userId);
                    created.setTopicId(topic.getId());
                    return created;
                });
        double previousMastery = progress.getMasteryScore() == null ? 0 : progress.getMasteryScore().doubleValue();
        double mastery = progress.getAttempts() == 0
                ? score
                : 0.7 * score + 0.3 * previousMastery;
        double bossScore = Math.max(score, progress.getBossScore() == null ? 0 : progress.getBossScore().doubleValue());
        progress.setAttempts(progress.getAttempts() + total);
        progress.setCorrectCount(progress.getCorrectCount() + correct);
        progress.setMasteryScore(BigDecimal.valueOf(mastery).setScale(2, RoundingMode.HALF_UP));
        progress.setBossScore(BigDecimal.valueOf(bossScore).setScale(2, RoundingMode.HALF_UP));
        progress.setLastPracticedAt(Instant.now());
        progress.setNextReviewAt(Instant.now().plus(mastery >= 70 ? 3 : 1, ChronoUnit.DAYS));
        boolean mastered = mastery >= 70 && bossScore >= 80;
        progress.setStatus(mastered ? GrammarStatus.MASTERED : GrammarStatus.IN_PROGRESS);
        progressRepository.save(progress);

        GrammarDtos.TopicDto next = nextTopic(userId).orElse(null);
        return new GrammarDtos.AttemptsResponse(
                total,
                correct,
                round(score),
                round(mastery),
                progress.getStatus().name(),
                next != null,
                next == null ? null : next.slug(),
                results
        );
    }

    @Transactional(readOnly = true)
    public Optional<GrammarDtos.TopicDto> nextTopic(Long userId) {
        GrammarDtos.GrammarPathResponse path = path(userId);
        for (GrammarDtos.WorldDto world : path.worlds()) {
            for (GrammarDtos.TopicDto topic : world.topics()) {
                if (!topic.locked() && !topic.completed()) {
                    return Optional.of(topic);
                }
            }
        }
        return Optional.empty();
    }

    private GrammarDtos.TopicDto toTopicDto(GrammarTopic topic,
                                            GrammarProgress progress,
                                            Map<String, GrammarTopic> bySlug,
                                            Map<Long, GrammarProgress> progressById) {
        List<String> prerequisites = readJson(topic.getPrerequisites(), new TypeReference<>() {
        }, List.of());
        boolean prerequisiteMet = prerequisites.stream().allMatch(slug -> {
            GrammarTopic prerequisite = bySlug.get(slug);
            if (prerequisite == null) {
                return true;
            }
            GrammarProgress prerequisiteProgress = progressById.get(prerequisite.getId());
            return prerequisiteProgress != null
                    && prerequisiteProgress.getMasteryScore() != null
                    && prerequisiteProgress.getMasteryScore().doubleValue() >= 70;
        });
        GrammarStatus status;
        if (progress != null && progress.getStatus() == GrammarStatus.MASTERED) {
            status = GrammarStatus.MASTERED;
        } else if (progress != null && progress.getStatus() == GrammarStatus.IN_PROGRESS) {
            status = GrammarStatus.IN_PROGRESS;
        } else if (prerequisites.isEmpty() || prerequisiteMet) {
            status = GrammarStatus.AVAILABLE;
        } else {
            status = GrammarStatus.LOCKED;
        }
        double mastery = progress == null || progress.getMasteryScore() == null
                ? 0 : progress.getMasteryScore().doubleValue();
        return new GrammarDtos.TopicDto(
                topic.getId(),
                topic.getSlug(),
                topic.getTitle(),
                topic.getCefrLevel(),
                topic.getSummary(),
                status.name(),
                round(mastery),
                prerequisites,
                status == GrammarStatus.LOCKED,
                status == GrammarStatus.MASTERED,
                topic.getSortOrder()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private double round(BigDecimal value) {
        return value == null ? 0 : value.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    public record StoredExercise(String id, String type, String prompt, List<String> options,
                                 String answer, String explanation) {
    }
}
