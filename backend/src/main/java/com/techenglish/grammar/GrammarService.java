package com.techenglish.grammar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.ai.AiGateway;
import com.techenglish.ai.GrammarGenDtos;
import com.techenglish.common.ApiException;
import com.techenglish.identity.LearnerProfile;
import com.techenglish.identity.LearnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrammarService {

    /** Questions served in one practice round (3 seed + 3 AI when the pool is ready). */
    private static final int PRACTICE_SIZE = 6;
    /** AI pool target per mode; a ready topic has 5 practice and 5 boss questions. */
    private static final int AI_POOL_SIZE = 5;
    /** Boss always uses a 5-question pool when AI is available. */
    private static final int BOSS_SIZE = 5;
    private static final int PASS_SCORE = 80;
    private static final int RECENT_WRONG_LIMIT = 5;

    private static final String POOL_PRACTICE = "PRACTICE";
    private static final String POOL_BOSS = "BOSS";
    private static final String MODE_PRACTICE = "PRACTICE";
    private static final String MODE_BOSS = "BOSS";
    private static final String SOURCE_SEED = "SEED";
    private static final String SOURCE_AI = "AI";

    private final GrammarTopicRepository topicRepository;
    private final GrammarLessonRepository lessonRepository;
    private final GrammarProgressRepository progressRepository;
    private final ExerciseAttemptRepository attemptRepository;
    private final GrammarExerciseRepository exerciseRepository;
    private final LearnerProfileRepository profileRepository;
    private final AiGateway aiGateway;
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
        return new GrammarDtos.GrammarPathResponse(worlds, overallMastery(topics, progress));
    }

    /** Grammar mastery across every published topic, not just the ones already practised. */
    @Transactional(readOnly = true)
    public double overallMastery(Long userId) {
        List<GrammarTopic> topics = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc();
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        return overallMastery(topics, progress);
    }

    private double overallMastery(List<GrammarTopic> topics, Map<Long, GrammarProgress> progress) {
        if (topics.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (GrammarTopic topic : topics) {
            GrammarProgress topicProgress = progress.get(topic.getId());
            sum += topicProgress == null || topicProgress.getMasteryScore() == null
                    ? 0 : topicProgress.getMasteryScore().doubleValue();
        }
        return round(sum / topics.size());
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

        LessonPools pools = buildPools(topic.getId(), lesson);
        boolean aiConfigured = aiConfigured();
        boolean generationNeeded = aiConfigured
                && (pools.aiPracticeCount() < AI_POOL_SIZE || pools.aiBossCount() < AI_POOL_SIZE);

        return new GrammarDtos.LessonResponse(
                topicDto,
                lesson.getContentMd(),
                readJson(lesson.getExamplesJson(), new TypeReference<List<GrammarDtos.ExampleDto>>() {
                }, List.of()),
                pools.practice().stream().map(this::toExerciseDto).toList(),
                pools.boss().stream().map(this::toExerciseDto).toList(),
                new GrammarDtos.PoolDto(
                        pools.practice().size(),
                        pools.boss().size(),
                        pools.bossSource(),
                        aiConfigured,
                        generationNeeded
                ),
                buildHistory(userId, topic.getId(), progress.get(topic.getId()), pools)
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

        String mode = normalizeMode(request.mode());
        LessonPools pools = buildPools(topic.getId(), lesson);
        List<PoolExercise> assigned = MODE_BOSS.equals(mode) ? pools.boss() : pools.practice();
        Map<String, PoolExercise> assignedById = assigned.stream()
                .collect(Collectors.toMap(PoolExercise::id, Function.identity()));

        Map<String, String> submitted = new LinkedHashMap<>();
        for (GrammarDtos.AttemptAnswer answer : request.answers()) {
            if (submitted.putIfAbsent(answer.exerciseId(), answer.answer()) != null) {
                throw ApiException.badRequest("DUPLICATE_ANSWER", "同一道题提交了多次答案");
            }
        }
        if (assigned.isEmpty()) {
            throw ApiException.badRequest("NO_EXERCISES", "这个节点还没有可提交的题目");
        }
        if (MODE_BOSS.equals(mode)) {
            // The boss set is fixed, so the submitted keys must match it exactly.
            if (!assignedById.keySet().equals(submitted.keySet())) {
                throw ApiException.badRequest("EXERCISE_SET_CHANGED", "Boss 题目已更新，请刷新后重试");
            }
        } else {
            // Practice selection is re-shuffled on every request, so grade the
            // questions the client actually answered instead of the newly drawn
            // set. All submitted ids must still belong to the topic's practice pool.
            Set<String> practiceIds = pools.practicePool().stream()
                    .map(PoolExercise::id)
                    .collect(Collectors.toSet());
            if (!practiceIds.containsAll(submitted.keySet())) {
                throw ApiException.badRequest("EXERCISE_SET_CHANGED", "练习题目已更新，请刷新后重试");
            }
        }

        List<PoolExercise> graded;
        if (MODE_BOSS.equals(mode)) {
            graded = assigned;
        } else {
            Map<String, PoolExercise> practiceById = pools.practicePool().stream()
                    .collect(Collectors.toMap(PoolExercise::id, Function.identity()));
            graded = submitted.keySet().stream()
                    .map(practiceById::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        if (graded.isEmpty()) {
            throw ApiException.badRequest("NO_VALID_ANSWERS", "没有提交有效的练习题");
        }

        List<GrammarDtos.ExerciseResult> results = new ArrayList<>();
        int correct = 0;
        for (PoolExercise exercise : graded) {
            String answer = submitted.get(exercise.id());
            if (answer == null) {
                throw ApiException.badRequest("INCOMPLETE_PRACTICE", "题目状态已变化，请刷新后重新作答");
            }
            boolean isCorrect = normalize(answer).equals(normalize(exercise.answer()));
            if (isCorrect) {
                correct++;
            }
            ExerciseAttempt attempt = new ExerciseAttempt();
            attempt.setUserId(userId);
            attempt.setTopicId(topic.getId());
            attempt.setExerciseKey(exercise.id());
            attempt.setAnswer(answer);
            attempt.setCorrect(isCorrect);
            attemptRepository.save(attempt);
            results.add(new GrammarDtos.ExerciseResult(
                    exercise.id(),
                    exercise.prompt(),
                    isCorrect,
                    answer,
                    exercise.answer(),
                    exercise.explanation(),
                    optionNotesMap(exercise)
            ));
        }

        int total = graded.size();
        double score = correct * 100.0 / total;
        GrammarProgress progress = progressRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> {
                    GrammarProgress created = new GrammarProgress();
                    created.setUserId(userId);
                    created.setTopicId(topic.getId());
                    return created;
                });
        boolean wasMastered = progress.getStatus() == GrammarStatus.MASTERED;
        double previousMastery = progress.getMasteryScore() == null ? 0 : progress.getMasteryScore().doubleValue();
        double previousBoss = progress.getBossScore() == null ? 0 : progress.getBossScore().doubleValue();
        double mastery = previousMastery;
        double bossScore = previousBoss;
        boolean passed = false;
        boolean mastered = wasMastered;

        if (MODE_BOSS.equals(mode)) {
            bossScore = Math.max(previousBoss, score);
            mastery = Math.max(previousMastery, score);
            passed = score >= PASS_SCORE;
            mastered = wasMastered || bossScore >= PASS_SCORE;
            progress.setBossScore(BigDecimal.valueOf(bossScore).setScale(2, RoundingMode.HALF_UP));
            progress.setNextReviewAt(Instant.now().plus(mastered ? 3 : 1, ChronoUnit.DAYS));
        }
        progress.setMasteryScore(BigDecimal.valueOf(mastery).setScale(2, RoundingMode.HALF_UP));
        progress.setStatus(mastered ? GrammarStatus.MASTERED : GrammarStatus.IN_PROGRESS);
        progress.setAttempts(progress.getAttempts() + total);
        progress.setCorrectCount(progress.getCorrectCount() + correct);
        progress.setLastPracticedAt(Instant.now());
        progressRepository.save(progress);

        boolean newlyMastered = MODE_BOSS.equals(mode) && !wasMastered && mastered;
        GrammarDtos.TopicDto next = newlyMastered ? nextTopic(userId).orElse(null) : null;
        return new GrammarDtos.AttemptsResponse(
                mode,
                total,
                correct,
                round(score),
                round(mastery),
                round(bossScore),
                PASS_SCORE,
                passed,
                progress.getStatus().name(),
                newlyMastered && next != null,
                next == null ? null : next.slug(),
                results
        );
    }

    /**
     * Lazily fills the AI question pool for one topic. The seed exercises remain in
     * grammar_lesson; generated items are cached in grammar_exercise so later visits
     * skip the model call. Called by the lesson page after it sees generationNeeded.
     */
    public GrammarDtos.GenerateResponse generateExercises(Long userId, String slug) {
        GrammarTopic topic = topicRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "语法节点不存在"));
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        Map<String, GrammarTopic> bySlug = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()
                .stream().collect(Collectors.toMap(GrammarTopic::getSlug, Function.identity()));
        if (toTopicDto(topic, progress.get(topic.getId()), bySlug, progress).locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        if (!aiConfigured()) {
            throw ApiException.badRequest("AI_NOT_CONFIGURED", "当前 AI 为演示模式或尚未配置，无法生成新题");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));
        List<GrammarExercise> existing = exerciseRepository
                .findByTopicIdAndDeletedOrderByIdAsc(topic.getId(), 0);
        long existingPractice = existing.stream().filter(e -> POOL_PRACTICE.equals(e.getPool())).count();
        long existingBoss = existing.stream().filter(e -> POOL_BOSS.equals(e.getPool())).count();
        if (existingPractice >= AI_POOL_SIZE && existingBoss >= AI_POOL_SIZE) {
            GrammarExercise first = existing.get(0);
            return new GrammarDtos.GenerateResponse(first.getProvider(), first.getModel(),
                    first.getPromptVersion(), 0, 0, true, true);
        }

        List<PoolExercise> seed = seedExercises(lesson);
        String techStack = profileRepository.findByUserId(userId)
                .map(this::techStackOf)
                .filter(value -> !value.isBlank())
                .orElse("Java, backend APIs, code review");
        String context = lessonContext(lesson);

        // Generate each pool in its own call. One mixed call was not reliable:
        // the model would return practice-only items and silently ignore the
        // boss half, so mode is now an explicit input and the server owns the pool.
        String provider = null;
        String model = null;
        String promptVersion = null;
        List<PoolExercise> practiceCandidates = List.of();
        List<PoolExercise> bossCandidates = List.of();
        RuntimeException practiceFailure = null;
        RuntimeException bossFailure = null;
        if (existingPractice < AI_POOL_SIZE) {
            try {
                GenerationResult result = generatePool(userId, topic, seed, existing, techStack, context, MODE_PRACTICE);
                practiceCandidates = result.candidates();
                provider = result.provider();
                model = result.model();
                promptVersion = result.promptVersion();
            } catch (RuntimeException ex) {
                practiceFailure = ex;
            }
        }
        if (existingBoss < AI_POOL_SIZE) {
            try {
                GenerationResult result = generatePool(userId, topic, seed, existing, techStack, context, MODE_BOSS);
                bossCandidates = result.candidates();
                provider = result.provider();
                model = result.model();
                promptVersion = result.promptVersion();
            } catch (RuntimeException ex) {
                bossFailure = ex;
            }
        }

        int practiceAdded = saveGenerated(practiceCandidates, topic.getId(), provider, model, promptVersion,
                (int) Math.max(0, AI_POOL_SIZE - existingPractice));
        int bossAdded = saveGenerated(bossCandidates, topic.getId(), provider, model, promptVersion,
                (int) Math.max(0, AI_POOL_SIZE - existingBoss));
        if (practiceAdded == 0 && bossAdded == 0) {
            if (practiceFailure != null) {
                throw practiceFailure;
            }
            if (bossFailure != null) {
                throw bossFailure;
            }
            throw ApiException.unavailable("AI 出题质量不达标，请重试");
        }
        return new GrammarDtos.GenerateResponse(provider, model, promptVersion, practiceAdded, bossAdded,
                existingPractice + practiceAdded >= AI_POOL_SIZE,
                existingBoss + bossAdded >= AI_POOL_SIZE);
    }

    private GenerationResult generatePool(Long userId, GrammarTopic topic, List<PoolExercise> seed,
                                          List<GrammarExercise> existing, String techStack,
                                          String context, String mode) {
        GrammarGenDtos.ExerciseGenResponse generated = aiGateway.generateGrammarExercises(userId, Map.of(
                "point", topic.getTitle() + " — " + topic.getSummary(),
                "level", topic.getCefrLevel() == null ? "A2" : topic.getCefrLevel(),
                "mode", mode,
                // Boss items are wordy (full-sentence options), so ask for exactly
                // the pool size; practice gets one spare item. Keeping the batch
                // small is what prevents the model output cap from truncating JSON.
                "count", String.valueOf(MODE_BOSS.equals(mode) ? AI_POOL_SIZE : AI_POOL_SIZE + 1),
                "techStack", techStack,
                "existing", concatPrompts(seed, existing),
                "context", context));

        Set<String> seen = new HashSet<>();
        seed.forEach(item -> seen.add(normalizePrompt(item.prompt())));
        existing.forEach(item -> seen.add(normalizePrompt(item.getPrompt())));
        List<PoolExercise> candidates = new ArrayList<>();
        for (GrammarGenDtos.GenExercise raw : generated.exercises()) {
            Optional<PoolExercise> validated = validateGenerated(raw, mode);
            if (validated.isEmpty()) {
                continue;
            }
            PoolExercise item = validated.get();
            if (!seen.add(normalizePrompt(item.prompt()))) {
                continue;
            }
            candidates.add(item);
            if (candidates.size() >= AI_POOL_SIZE) {
                break;
            }
        }
        if (candidates.size() < AI_POOL_SIZE) {
            throw ApiException.unavailable("AI 出题质量不达标，请重试");
        }
        return new GenerationResult(candidates, generated.provider(), generated.model(),
                generated.promptVersion());
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
                    && prerequisiteProgress.getStatus() == GrammarStatus.MASTERED;
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

    private LessonPools buildPools(Long topicId, GrammarLesson lesson) {
        List<PoolExercise> seed = seedExercises(lesson);
        List<GrammarExercise> aiRows = exerciseRepository
                .findByTopicIdAndDeletedOrderByIdAsc(topicId, 0);
        List<PoolExercise> aiPractice = aiRows.stream()
                .filter(row -> POOL_PRACTICE.equals(row.getPool()))
                .map(this::toPoolExercise)
                .toList();
        List<PoolExercise> aiBoss = aiRows.stream()
                .filter(row -> POOL_BOSS.equals(row.getPool()))
                .map(this::toPoolExercise)
                .toList();

        List<PoolExercise> practicePool = new ArrayList<>(seed);
        practicePool.addAll(aiPractice);

        List<PoolExercise> practice = new ArrayList<>(seed.subList(0, Math.min(seed.size(), PRACTICE_SIZE)));
        List<PoolExercise> shuffled = new ArrayList<>(aiPractice);
        Collections.shuffle(shuffled);
        int room = PRACTICE_SIZE - practice.size();
        if (room > 0) {
            practice.addAll(shuffled.subList(0, Math.min(room, shuffled.size())));
        }

        List<PoolExercise> boss;
        String bossSource;
        if (aiBoss.size() >= AI_POOL_SIZE) {
            boss = List.copyOf(aiBoss.subList(0, BOSS_SIZE));
            bossSource = SOURCE_AI;
        } else {
            // Offline/demo fallback: the three seed questions stay playable, but a
            // perfect score is required because 2/3 is below the 80% pass line.
            boss = List.copyOf(seed.subList(0, Math.min(seed.size(), PRACTICE_SIZE)));
            bossSource = SOURCE_SEED;
        }
        return new LessonPools(practice, boss, List.copyOf(practicePool),
                aiPractice.size(), aiBoss.size(), bossSource);
    }

    private GrammarDtos.HistoryDto buildHistory(Long userId, Long topicId, GrammarProgress progress,
                                                LessonPools pools) {
        long attempts = progress == null || progress.getAttempts() == 0 ? 0 : progress.getAttempts();
        long correct = progress == null ? 0 : progress.getCorrectCount();
        Double bossScore = progress == null || progress.getBossScore() == null
                ? null : progress.getBossScore().doubleValue();
        Instant lastPracticedAt = progress == null ? null : progress.getLastPracticedAt();

        Map<String, PoolExercise> byId = new HashMap<>();
        pools.practicePool().forEach(item -> byId.put(item.id(), item));
        pools.boss().forEach(item -> byId.put(item.id(), item));
        List<GrammarDtos.WrongAnswerDto> recentWrong = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ExerciseAttempt attempt : attemptRepository
                .findTop30ByUserIdAndTopicIdOrderByIdDesc(userId, topicId)) {
            if (attempt.isCorrect() || !seen.add(attempt.getExerciseKey())) {
                continue;
            }
            PoolExercise exercise = byId.get(attempt.getExerciseKey());
            if (exercise == null) {
                continue;
            }
            recentWrong.add(new GrammarDtos.WrongAnswerDto(
                    exercise.id(), exercise.prompt(), attempt.getAnswer(), exercise.answer()));
            if (recentWrong.size() >= RECENT_WRONG_LIMIT) {
                break;
            }
        }
        return new GrammarDtos.HistoryDto(attempts, correct, lastPracticedAt, bossScore, recentWrong);
    }

    private List<PoolExercise> seedExercises(GrammarLesson lesson) {
        return readJson(lesson.getExercisesJson(), new TypeReference<List<StoredExercise>>() {
        }, List.of()).stream()
                .filter(Objects::nonNull)
                .filter(exercise -> exercise.id() != null && !exercise.id().isBlank())
                .map(exercise -> new PoolExercise(
                        exercise.id(),
                        exercise.type() == null ? "MCQ" : exercise.type(),
                        exercise.prompt(),
                        exercise.options() == null ? List.of() : exercise.options(),
                        exercise.answer() == null ? "" : exercise.answer(),
                        exercise.explanation() == null ? "" : exercise.explanation(),
                        List.of(),
                        null,
                        null,
                        SOURCE_SEED,
                        POOL_PRACTICE
                ))
                .toList();
    }

    private PoolExercise toPoolExercise(GrammarExercise row) {
        return new PoolExercise(
                row.getExerciseKey(),
                row.getType(),
                row.getPrompt(),
                readStringList(row.getOptionsJson()),
                row.getAnswer(),
                row.getExplanation() == null ? "" : row.getExplanation(),
                readStringList(row.getOptionNotesJson()),
                row.getSkill(),
                row.getLevel(),
                row.getSource(),
                row.getPool()
        );
    }

    private GrammarDtos.ExerciseDto toExerciseDto(PoolExercise exercise) {
        return new GrammarDtos.ExerciseDto(
                exercise.id(),
                exercise.type(),
                exercise.prompt(),
                exercise.options(),
                exercise.skill(),
                exercise.level(),
                exercise.source()
        );
    }

    private Optional<PoolExercise> validateGenerated(GrammarGenDtos.GenExercise raw, String mode) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw.type() != null && !"MCQ".equalsIgnoreCase(raw.type().trim())) {
            return Optional.empty();
        }
        String prompt = truncate(raw.prompt(), 500);
        if (prompt.isBlank()) {
            return Optional.empty();
        }
        List<String> options = raw.options() == null ? List.of() : raw.options().stream()
                .filter(Objects::nonNull)
                .map(this::stripOptionPrefix)
                .map(option -> truncate(option, 300))
                .filter(option -> !option.isBlank())
                .distinct()
                .toList();
        if (options.size() != 4) {
            return Optional.empty();
        }
        String answer = canonicalAnswer(raw.answer(), options);
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        String explanation = truncate(raw.explanation(), 2000);
        if (explanation.isBlank()) {
            return Optional.empty();
        }
        List<String> notes = raw.optionNotes() == null ? List.of() : raw.optionNotes().stream()
                .map(note -> truncate(note, 500))
                .toList();
        if (notes.size() != options.size() || notes.stream().anyMatch(String::isBlank)) {
            return Optional.empty();
        }
        Integer level = raw.level() == null ? null : Math.max(1, Math.min(4, raw.level()));
        return Optional.of(new PoolExercise(
                "ai-" + UUID.randomUUID(),
                "MCQ",
                prompt,
                options,
                answer,
                explanation,
                notes,
                normalizeSkill(raw.skill()),
                level,
                SOURCE_AI,
                mode
        ));
    }

    private int saveGenerated(List<PoolExercise> candidates, Long topicId,
                              String provider, String model, String promptVersion, int capacity) {
        int saved = 0;
        for (PoolExercise candidate : candidates) {
            if (saved >= capacity) {
                break;
            }
            GrammarExercise entity = new GrammarExercise();
            entity.setExerciseKey(candidate.id());
            entity.setTopicId(topicId);
            entity.setPool(candidate.pool());
            entity.setType(candidate.type());
            entity.setSkill(candidate.skill());
            entity.setLevel(candidate.level());
            entity.setPrompt(candidate.prompt());
            entity.setOptionsJson(writeJson(candidate.options()));
            entity.setAnswer(candidate.answer());
            entity.setExplanation(candidate.explanation());
            entity.setOptionNotesJson(writeJson(candidate.optionNotes()));
            entity.setSource(SOURCE_AI);
            entity.setPromptVersion(promptVersion);
            entity.setProvider(provider);
            entity.setModel(model);
            exerciseRepository.save(entity);
            saved++;
        }
        return saved;
    }

    private Map<String, String> optionNotesMap(PoolExercise exercise) {
        Map<String, String> notes = new LinkedHashMap<>();
        for (int i = 0; i < exercise.options().size() && i < exercise.optionNotes().size(); i++) {
            String note = exercise.optionNotes().get(i);
            if (note != null && !note.isBlank()) {
                notes.put(exercise.options().get(i), note);
            }
        }
        return notes;
    }

    private String normalizeMode(String mode) {
        String value = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return MODE_PRACTICE;
        }
        if (!MODE_PRACTICE.equals(value) && !MODE_BOSS.equals(value)) {
            throw ApiException.badRequest("INVALID_MODE", "只支持 PRACTICE / BOSS 两种练习模式");
        }
        return value;
    }

    private String normalizeSkill(String skill) {
        if (skill == null) {
            return null;
        }
        String value = skill.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "recognize", "recognition" -> "recognize";
            case "form", "form-meaning" -> "form";
            case "use", "usage", "production" -> "use";
            default -> null;
        };
    }

    /** Strips only a real option label ("A. ", "B) ", ...), never a leading article. */
    private String stripOptionPrefix(String option) {
        return option == null ? "" : option.replaceFirst("^\\s*[A-Da-d]\\s*[.)、:]\\s*", "").trim();
    }

    private String canonicalAnswer(String rawAnswer, List<String> options) {
        if (rawAnswer == null) {
            return "";
        }
        String value = rawAnswer.trim();
        if (value.isEmpty()) {
            return "";
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(value)) {
                return option;
            }
        }
        String candidate = value.replaceFirst("^[A-Da-d]\\s*[.)、:]\\s*", "").trim();
        for (String option : options) {
            if (option.equalsIgnoreCase(candidate)) {
                return option;
            }
        }
        if (value.length() == 1) {
            int index = Character.toUpperCase(value.charAt(0)) - 'A';
            if (index >= 0 && index < options.size()) {
                return options.get(index);
            }
        }
        return "";
    }

    private boolean aiConfigured() {
        var status = aiGateway.status();
        return status.configured() && !"mock".equalsIgnoreCase(status.provider());
    }

    private String techStackOf(LearnerProfile profile) {
        return String.join(", ", readJson(profile.getTechStack(), new TypeReference<List<String>>() {
        }, List.of()));
    }

    private String concatPrompts(List<PoolExercise> seed, List<GrammarExercise> existing) {
        List<String> prompts = new ArrayList<>();
        seed.forEach(item -> prompts.add(item.prompt()));
        existing.forEach(item -> prompts.add(item.getPrompt()));
        return prompts.stream()
                .limit(20)
                .map(prompt -> "- " + truncate(prompt, 200))
                .collect(Collectors.joining("\n"));
    }

    private String lessonContext(GrammarLesson lesson) {
        String content = truncate(lesson.getContentMd(), 3000);
        String examples = readJson(lesson.getExamplesJson(),
                new TypeReference<List<GrammarDtos.ExampleDto>>() {
                }, List.of()).stream()
                .limit(3)
                .map(example -> example.sentence() + " — " + example.translation())
                .collect(Collectors.joining("\n"));
        return content + "\n\nExamples:\n" + truncate(examples, 1200);
    }

    private String normalizePrompt(String prompt) {
        return prompt == null ? "" : prompt.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize grammar exercise", ex);
        }
    }

    private List<String> readStringList(String json) {
        return readJson(json, new TypeReference<List<String>>() {
        }, List.of());
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

    private record GenerationResult(
            List<PoolExercise> candidates,
            String provider,
            String model,
            String promptVersion
    ) {
    }

    private record LessonPools(
            List<PoolExercise> practice,
            List<PoolExercise> boss,
            List<PoolExercise> practicePool,
            int aiPracticeCount,
            int aiBossCount,
            String bossSource
    ) {
    }

    private record PoolExercise(
            String id,
            String type,
            String prompt,
            List<String> options,
            String answer,
            String explanation,
            List<String> optionNotes,
            String skill,
            Integer level,
            String source,
            String pool
    ) {
    }

    public record StoredExercise(String id, String type, String prompt, List<String> options,
                                 String answer, String explanation) {
    }
}
