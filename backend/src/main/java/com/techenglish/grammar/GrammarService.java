package com.techenglish.grammar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.ai.AiGateway;
import com.techenglish.ai.GrammarGenDtos;
import com.techenglish.common.ApiException;
import com.techenglish.identity.LearnerProfile;
import com.techenglish.identity.LearnerProfileRepository;
import com.techenglish.question.Question;
import com.techenglish.question.QuestionAttempt;
import com.techenglish.question.QuestionAttemptRepository;
import com.techenglish.question.QuestionRepository;
import com.techenglish.question.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrammarService {

    /** Questions served in one practice round (3 seed + 3 AI when the pool is ready). */
    private static final int PRACTICE_SIZE = 6;
    /** AI practice pool target; a ready topic has 5 generated practice questions. */
    private static final int AI_POOL_SIZE = 5;
    /** AI boss pool target. Larger than the paper on purpose: a retake rotates away
     *  from the questions whose answers were just revealed, so the second run is not
     *  a memory test of the failed first run. */
    private static final int BOSS_POOL_SIZE = 8;
    /** Boss paper size. */
    private static final int BOSS_SIZE = 5;
    /** Cap for one AI generation batch; larger batches hit model output truncation. */
    private static final int GEN_BATCH_CAP = 6;
    private static final int PASS_SCORE = 80;
    private static final int RECENT_WRONG_LIMIT = 5;
    /** Wrong-answer re-practice slots in one boss paper, so the rest stay fresh. */
    private static final int WRONG_REVIEW_CAP = 3;
    private static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final String DEFAULT_WORLD = "自定义知识点";
    private static final String PLACEHOLDER_CONTENT_MD = """
            ## 核心规则

            （讲义待补充：写下这个知识点的规则要点。）

            ## 怎么用

            （讲义待补充：给出 2-3 个例句，说明使用场景。）

            ## 中国学习者常见错误

            （讲义待补充：记录你踩过的坑。）

            > 学习建议：先补全讲义，再做练习。题目会由 AI 按讲义内容自动生成。
            """;

    private static final String POOL_PRACTICE = "PRACTICE";
    private static final String POOL_BOSS = "BOSS";
    private static final String MODE_PRACTICE = "PRACTICE";
    private static final String MODE_BOSS = "BOSS";
    private static final String SOURCE_SEED = "SEED";
    private static final String SOURCE_AI = "AI";
    /** Objective items: answer is matched against an option string. */
    private static final String TYPE_MCQ = "MCQ";

    private final GrammarTopicRepository topicRepository;
    private final GrammarTopicEdgeRepository edgeRepository;
    private final GrammarLessonRepository lessonRepository;
    private final GrammarProgressRepository progressRepository;
    private final QuestionAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final LearnerProfileRepository profileRepository;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    /** submit() grades open answers through a slow AI call: the reads and the
     *  grading run outside any transaction and only the persisted results get a
     *  short programmatic one, so a slow model never holds the SQLite write lock. */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public GrammarDtos.GrammarPathResponse path(Long userId) {
        List<GrammarTopic> topics = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc();
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        PrereqGraph graph = loadPrereqGraph(topics);
        List<GrammarDtos.TopicDto> topicDtos = topics.stream()
                .map(topic -> toTopicDto(topic, progress.get(topic.getId()), graph, progress))
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
        PrereqGraph graph = loadPrereqGraph(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc());
        GrammarProgress topicProgress = progress.get(topic.getId());
        GrammarDtos.TopicDto topicDto = toTopicDto(topic, topicProgress, graph, progress);
        if (topicDto.locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));

        LessonPools pools = buildPools(userId, topic.getId(), topicProgress);
        boolean aiConfigured = aiConfigured();
        boolean generationNeeded = aiConfigured
                && (pools.aiPracticeCount() < AI_POOL_SIZE || pools.aiBossCount() < BOSS_POOL_SIZE);
        // Proficiency gate for "skip the practice set": mastered nodes review via boss
        // anyway; before mastery, once every practice-pool question has been answered
        // correctly at least time the fixed set only re-reveals known answers.
        boolean recommendBoss = topicDto.completed()
                || (SOURCE_AI.equals(pools.bossSource())
                && practicePoolExhausted(userId, topic.getId(), pools.practicePool()));

        return new GrammarDtos.LessonResponse(
                topicDto,
                lesson.getContentMd(),
                readJson(lesson.getExamplesJson(), new TypeReference<List<GrammarDtos.ExampleDto>>() {
                }, List.of()),
                pools.practice().stream().map(exercise -> toExerciseDto(exercise, false)).toList(),
                pools.boss().stream()
                        .map(exercise -> toExerciseDto(exercise, pools.bossWrongKeys().contains(exercise.id())))
                        .toList(),
                new GrammarDtos.PoolDto(
                        pools.practice().size(),
                        pools.boss().size(),
                        pools.bossSource(),
                        aiConfigured,
                        generationNeeded
                ),
                buildHistory(userId, topic.getId(), topicProgress, pools),
                recommendBoss
        );
    }

    /** Fresh boss paper for a retake ("换题再战"). Same rotation as lesson(); the
     *  client keeps its practice feedback state instead of reloading the whole lesson. */
    @Transactional(readOnly = true)
    public GrammarDtos.BossDrawResponse drawBoss(Long userId, String slug) {
        GrammarTopic topic = topicRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "语法节点不存在"));
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        PrereqGraph graph = loadPrereqGraph(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc());
        GrammarProgress topicProgress = progress.get(topic.getId());
        if (toTopicDto(topic, topicProgress, graph, progress).locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));
        LessonPools pools = buildPools(userId, topic.getId(), topicProgress);
        return new GrammarDtos.BossDrawResponse(
                pools.boss().stream()
                        .map(exercise -> toExerciseDto(exercise, pools.bossWrongKeys().contains(exercise.id())))
                        .toList(),
                pools.bossSource());
    }

    /** Whole knowledge tree (nodes with mastery + dependency edges) for the visualisation view. */
    @Transactional(readOnly = true)
    public GrammarDtos.TreeResponse tree(Long userId) {
        List<GrammarTopic> topics = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc();
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        PrereqGraph graph = loadPrereqGraph(topics);
        List<GrammarDtos.TreeNodeDto> nodes = topics.stream().map(topic -> {
            GrammarProgress topicProgress = progress.get(topic.getId());
            return toTreeNodeDto(topic, toTopicDto(topic, topicProgress, graph, progress), topicProgress);
        }).toList();
        List<GrammarDtos.TreeEdgeDto> edges = edgeRepository.findByDeleted(0).stream()
                .filter(edge -> graph.slugById().containsKey(edge.getUpTopicId())
                        && graph.slugById().containsKey(edge.getDownTopicId()))
                .map(edge -> new GrammarDtos.TreeEdgeDto(
                        graph.slugById().get(edge.getUpTopicId()),
                        graph.slugById().get(edge.getDownTopicId()),
                        edge.getEdgeType()))
                .toList();
        return new GrammarDtos.TreeResponse(nodes, edges, overallMastery(topics, progress));
    }

    private GrammarDtos.TreeNodeDto toTreeNodeDto(GrammarTopic topic, GrammarDtos.TopicDto dto,
                                                  GrammarProgress progress) {
        return new GrammarDtos.TreeNodeDto(
                dto.id(),
                dto.slug(),
                dto.title(),
                topic.getWorld(),
                topic.getWorldOrder(),
                dto.sortOrder(),
                dto.cefrLevel(),
                dto.summary(),
                topic.getSource(),
                dto.status(),
                dto.mastery(),
                dto.locked(),
                dto.completed(),
                dto.nextReviewAt(),
                progress == null ? null : progress.getLastPracticedAt(),
                topic.getOriginArticleId(),
                topic.getOriginSentence());
    }

    /** Adds a user-defined knowledge point: topic + lesson (placeholder讲义 when omitted) + prerequisite edges. */
    @Transactional
    public GrammarDtos.TreeNodeDto createTopic(Long userId, GrammarDtos.TopicCreateRequest request) {
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        if (topicRepository.existsBySlugAndDeleted(slug, 0)) {
            throw ApiException.badRequest("SLUG_EXISTS", "标识符 " + slug + " 已存在");
        }
        String cefrLevel = request.cefrLevel() == null || request.cefrLevel().isBlank()
                ? "A2" : request.cefrLevel().trim().toUpperCase(Locale.ROOT);
        if (!CEFR_LEVELS.contains(cefrLevel)) {
            throw ApiException.badRequest("INVALID_CEFR", "CEFR 等级只能是 A1/A2/B1/B2/C1/C2");
        }
        List<GrammarTopic> published = topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc();
        Map<String, GrammarTopic> bySlug = published.stream()
                .collect(Collectors.toMap(GrammarTopic::getSlug, Function.identity()));
        List<String> prereqSlugs = request.prerequisites() == null ? List.of()
                : request.prerequisites().stream()
                        .filter(Objects::nonNull)
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .filter(value -> !value.isBlank() && !value.equals(slug))
                        .distinct()
                        .toList();
        List<String> unknown = prereqSlugs.stream().filter(value -> !bySlug.containsKey(value)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("UNKNOWN_PREREQUISITE", "前置知识点不存在: " + String.join(", ", unknown));
        }
        // No cycle check needed: a brand-new node has no incoming edges, so its outgoing
        // edges cannot close a loop. A future "connect two existing nodes" API would need
        // a reachability check over grammar_topic_edge.
        String world = request.world() == null || request.world().isBlank()
                ? DEFAULT_WORLD : request.world().trim();
        int worldOrder = request.worldOrder() != null ? request.worldOrder()
                : published.stream().filter(topic -> topic.getWorld().equals(world))
                        .mapToInt(GrammarTopic::getWorldOrder).max()
                        .orElseGet(() -> published.stream().mapToInt(GrammarTopic::getWorldOrder).max().orElse(0) + 1);
        int sortOrder = published.stream().filter(topic -> topic.getWorld().equals(world))
                .mapToInt(GrammarTopic::getSortOrder).max().orElse(0) + 1;

        GrammarTopic topic = new GrammarTopic();
        topic.setSlug(slug);
        topic.setTitle(request.title().trim());
        topic.setWorld(world);
        topic.setWorldOrder(worldOrder);
        topic.setCefrLevel(cefrLevel);
        topic.setSummary(request.summary().trim());
        topic.setPrerequisites(writeJson(prereqSlugs));
        topic.setSortOrder(sortOrder);
        topic.setPublished(true);
        topic.setSource("USER");
        topic = topicRepository.save(topic);
        for (String prereqSlug : prereqSlugs) {
            edgeRepository.save(GrammarTopicEdge.prerequisite(topic.getId(), bySlug.get(prereqSlug).getId()));
        }

        GrammarLesson lesson = new GrammarLesson();
        lesson.setTopic(topic);
        lesson.setContentMd(request.contentMd() == null || request.contentMd().isBlank()
                ? PLACEHOLDER_CONTENT_MD : request.contentMd());
        lesson.setExamplesJson(writeJson(request.examples() == null ? List.of() : request.examples()));
        lesson.setExercisesJson("[]");
        lesson.setModel("user");
        lesson.setPromptVersion("user-v1");
        lessonRepository.save(lesson);

        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        PrereqGraph graph = loadPrereqGraph(
                topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc());
        return toTreeNodeDto(topic, toTopicDto(topic, null, graph, progress), null);
    }

    public GrammarDtos.AttemptsResponse submit(Long userId, String slug, GrammarDtos.AttemptsRequest request) {
        GrammarTopic topic = topicRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "语法节点不存在"));
        Map<Long, GrammarProgress> allProgress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        PrereqGraph graph = loadPrereqGraph(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc());
        if (toTopicDto(topic, allProgress.get(topic.getId()), graph, allProgress).locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));

        String mode = normalizeMode(request.mode());
        GrammarProgress progress = progressRepository.findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> {
                    GrammarProgress created = new GrammarProgress();
                    created.setUserId(userId);
                    created.setTopicId(topic.getId());
                    return created;
                });
        LessonPools pools = buildPools(userId, topic.getId(), progress);
        // BOSS accepts the boss pool plus wrong-answer review items (which can come from
        // the practice pool): the draw may put them on the paper and the candidate set is
        // monotonic, so a paper served earlier still validates here.
        List<PoolExercise> universe = MODE_BOSS.equals(mode) ? pools.bossUniverse() : pools.practicePool();
        Map<String, PoolExercise> universeById = universe.stream()
                .collect(Collectors.toMap(PoolExercise::id, Function.identity()));

        Map<String, String> submitted = new LinkedHashMap<>();
        for (GrammarDtos.AttemptAnswer answer : request.answers()) {
            if (submitted.putIfAbsent(answer.exerciseId(), answer.answer()) != null) {
                throw ApiException.badRequest("DUPLICATE_ANSWER", "同一道题提交了多次答案");
            }
        }
        if (universe.isEmpty()) {
            throw ApiException.badRequest("NO_EXERCISES", "这个节点还没有可提交的题目");
        }
        // Both modes grade the questions the client actually answered: the boss draw
        // rotates per round, so a server-side re-draw would not match the paper served
        // to the client. BOSS also requires the exact paper size, so a retake cannot
        // cherry-pick the easiest subset of the pool. Accepted trade-off: a determined
        // user could reload until they have seen the whole pool before answering —
        // self-study software, not worth server-side session state.
        if (!universeById.keySet().containsAll(submitted.keySet())) {
            throw ApiException.badRequest("EXERCISE_SET_CHANGED",
                    MODE_BOSS.equals(mode) ? "Boss 题目已更新，请刷新后重试" : "练习题目已更新，请刷新后重试");
        }
        if (MODE_BOSS.equals(mode) && submitted.size() != pools.boss().size()) {
            throw ApiException.badRequest("INCOMPLETE_PRACTICE", "题目状态已变化，请刷新后重新作答");
        }
        List<PoolExercise> graded = submitted.keySet().stream()
                .map(universeById::get)
                .filter(Objects::nonNull)
                .toList();

        if (graded.isEmpty()) {
            throw ApiException.badRequest("NO_VALID_ANSWERS", "没有提交有效的练习题");
        }

        // Open answers go to the AI judge before anything is written: a judge
        // failure fails the whole submission and the client (which keeps its typed
        // answers) can retry without half-persisted attempts.
        Map<String, GrammarGenDtos.JudgeVerdict> verdicts = gradeSubjective(userId, topic, graded, submitted);
        return transactionTemplate.execute(status ->
                persistSubmission(userId, topic, progress, mode, graded, submitted, verdicts));
    }

    /** Attempts, progress update and response building — runs inside one short transaction. */
    private GrammarDtos.AttemptsResponse persistSubmission(Long userId,
                                                           GrammarTopic topic,
                                                           GrammarProgress progress,
                                                           String mode,
                                                           List<PoolExercise> graded,
                                                           Map<String, String> submitted,
                                                           Map<String, GrammarGenDtos.JudgeVerdict> verdicts) {
        List<GrammarDtos.ExerciseResult> results = new ArrayList<>();
        int correct = 0;
        for (PoolExercise exercise : graded) {
            String answer = submitted.get(exercise.id());
            boolean subjective = isSubjective(exercise.type());
            GrammarGenDtos.JudgeVerdict verdict = subjective ? verdicts.get(exercise.id()) : null;
            boolean isCorrect = subjective
                    ? verdict != null && Boolean.TRUE.equals(verdict.correct())
                    : normalize(answer).equals(normalize(exercise.answer()));
            if (isCorrect) {
                correct++;
            }
            QuestionAttempt attempt = new QuestionAttempt();
            attempt.setUserId(userId);
            attempt.setDomain(Question.DOMAIN_GRAMMAR);
            attempt.setTopicId(topic.getId());
            attempt.setQuestionKey(exercise.id());
            attempt.setAnswer(answer);
            attempt.setCorrect(isCorrect);
            attemptRepository.save(attempt);
            results.add(new GrammarDtos.ExerciseResult(
                    exercise.id(),
                    exercise.prompt(),
                    isCorrect,
                    answer,
                    exercise.answer(),
                    subjective ? subjectiveFeedback(exercise, verdict) : exercise.explanation(),
                    optionNotesMap(exercise)
            ));
        }

        int total = graded.size();
        double score = correct * 100.0 / total;
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
        if (MODE_BOSS.equals(mode)) {
            // Rotation anchor: the next boss draw excludes these keys, so the paper the
            // user just saw (with answers revealed) cannot be farmed on a retake.
            progress.setLastBossIds(writeJson(graded.stream().map(PoolExercise::id).toList()));
            progress.setLastBossAt(Instant.now());
        }
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

    /** Open items (rewrite / translate / make-a-sentence): the learner types an
     *  English sentence and the exercise-judge AI call grades it against a reference. */
    private static boolean isSubjective(String type) {
        return QuestionType.isSubjective(type);
    }

    /**
     * One exercise-judge call for all open items of a submission; returns verdicts
     * keyed by exercise id. MCQ-only submissions skip the network entirely.
     */
    private Map<String, GrammarGenDtos.JudgeVerdict> gradeSubjective(
            Long userId, GrammarTopic topic, List<PoolExercise> graded, Map<String, String> submitted) {
        List<PoolExercise> subjective = graded.stream()
                .filter(exercise -> isSubjective(exercise.type()))
                .toList();
        if (subjective.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < subjective.size(); i++) {
            PoolExercise exercise = subjective.get(i);
            items.add(Map.of(
                    "index", i,
                    "type", exercise.type(),
                    "prompt", exercise.prompt(),
                    "reference", exercise.answer(),
                    "answer", submitted.getOrDefault(exercise.id(), "")));
        }
        String itemsJson;
        try {
            itemsJson = objectMapper.writeValueAsString(items);
        } catch (Exception ex) {
            throw ApiException.badRequest("INVALID_ANSWER", "答案格式不正确");
        }
        GrammarGenDtos.JudgePayload payload = aiGateway.judgeGrammarAnswers(userId, Map.of(
                "point", topic.getTitle() + " — " + topic.getSummary(),
                "items", itemsJson));

        Map<String, GrammarGenDtos.JudgeVerdict> byExercise = new HashMap<>();
        if (payload.verdicts() != null) {
            for (GrammarGenDtos.JudgeVerdict verdict : payload.verdicts()) {
                if (verdict == null || verdict.index() == null
                        || verdict.index() < 0 || verdict.index() >= subjective.size()) {
                    continue;
                }
                byExercise.putIfAbsent(subjective.get(verdict.index()).id(), verdict);
            }
        }
        return byExercise;
    }

    private String subjectiveFeedback(PoolExercise exercise, GrammarGenDtos.JudgeVerdict verdict) {
        if (verdict == null) {
            return "AI 未返回本题的判定，本题按未答对计，可直接参考答案：" + exercise.answer();
        }
        String feedback = verdict.feedback() == null ? "" : verdict.feedback().strip();
        return feedback.isEmpty() ? exercise.explanation() : feedback;
    }

    /**
     * Lazily fills the AI question pool for one topic. Seed and generated items both
     * live in the unified question bank, so later visits skip the model call.
     * Called by the lesson page after it sees generationNeeded.
     */
    public GrammarDtos.GenerateResponse generateExercises(Long userId, String slug) {
        GrammarTopic topic = topicRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> ApiException.notFound("TOPIC_NOT_FOUND", "语法节点不存在"));
        Map<Long, GrammarProgress> progress = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GrammarProgress::getTopicId, Function.identity()));
        PrereqGraph graph = loadPrereqGraph(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc());
        if (toTopicDto(topic, progress.get(topic.getId()), graph, progress).locked()) {
            throw ApiException.forbidden("TOPIC_LOCKED", "请先完成前置语法节点");
        }
        if (!aiConfigured()) {
            throw ApiException.badRequest("AI_NOT_CONFIGURED", "当前 AI 为演示模式或尚未配置，无法生成新题");
        }
        GrammarLesson lesson = lessonRepository.findByTopicId(topic.getId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_READY", "这节课还在准备中"));
        List<Question> rows = topicRows(topic.getId());
        List<Question> existing = rows.stream()
                .filter(row -> !SOURCE_SEED.equals(row.getSource()))
                .toList();
        long existingPractice = existing.stream().filter(e -> POOL_PRACTICE.equals(e.getPool())).count();
        long existingBoss = existing.stream().filter(e -> POOL_BOSS.equals(e.getPool())).count();
        int practiceGap = Math.max(0, AI_POOL_SIZE - (int) existingPractice);
        int bossGap = Math.max(0, BOSS_POOL_SIZE - (int) existingBoss);
        if (practiceGap == 0 && bossGap == 0) {
            Question first = existing.get(0);
            return new GrammarDtos.GenerateResponse(first.getProvider(), first.getModel(),
                    first.getPromptVersion(), 0, 0, true, true);
        }

        List<PoolExercise> seed = rows.stream()
                .filter(row -> SOURCE_SEED.equals(row.getSource()))
                .map(this::toPoolExercise)
                .toList();
        String techStack = profileRepository.findByUserId(userId)
                .map(this::techStackOf)
                .filter(value -> !value.isBlank())
                .orElse("Java, backend APIs, code review");
        String context = lessonContext(lesson);

        // Generate each pool in its own call. One mixed call was not reliable:
        // the model would return practice-only items and silently ignore the
        // boss half, so mode is now an explicit input and the server owns the pool.
        // A partial batch is kept instead of discarded: generationNeeded stays true
        // and the next visit tops up the remaining gap.
        String provider = null;
        String model = null;
        String promptVersion = null;
        List<PoolExercise> practiceCandidates = List.of();
        List<PoolExercise> bossCandidates = List.of();
        RuntimeException practiceFailure = null;
        RuntimeException bossFailure = null;
        if (practiceGap > 0) {
            try {
                GenerationResult result = generatePool(userId, topic, seed, existing, techStack, context,
                        MODE_PRACTICE, practiceGap);
                practiceCandidates = result.candidates();
                provider = result.provider();
                model = result.model();
                promptVersion = result.promptVersion();
            } catch (RuntimeException ex) {
                practiceFailure = ex;
            }
        }
        if (bossGap > 0) {
            try {
                GenerationResult result = generatePool(userId, topic, seed, existing, techStack, context,
                        MODE_BOSS, bossGap);
                bossCandidates = result.candidates();
                provider = result.provider();
                model = result.model();
                promptVersion = result.promptVersion();
            } catch (RuntimeException ex) {
                bossFailure = ex;
            }
        }

        int practiceAdded = saveGenerated(practiceCandidates, topic.getId(), provider, model, promptVersion,
                practiceGap);
        int bossAdded = saveGenerated(bossCandidates, topic.getId(), provider, model, promptVersion,
                bossGap);
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
                practiceAdded >= practiceGap,
                bossAdded >= bossGap);
    }

    private GenerationResult generatePool(Long userId, GrammarTopic topic, List<PoolExercise> seed,
                                          List<Question> existing, String techStack,
                                          String context, String mode, int required) {
        // Ask for one spare item beyond the gap; the batch stays capped because long
        // boss items (full-sentence options + per-option notes) risk truncating the JSON.
        // A short-but-non-empty batch is accepted: the gap shrinks and the next visit
        // tops up the remainder.
        int ask = Math.min(required + 1, GEN_BATCH_CAP);
        GrammarGenDtos.ExerciseGenResponse generated = aiGateway.generateGrammarExercises(userId, Map.of(
                "point", topic.getTitle() + " — " + topic.getSummary(),
                "level", topic.getCefrLevel() == null ? "A2" : topic.getCefrLevel(),
                "mode", mode,
                "count", String.valueOf(ask),
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
            if (candidates.size() >= required) {
                break;
            }
        }
        if (candidates.isEmpty()) {
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

    /**
     * Prerequisite graph loaded from grammar_topic_edge: downIds keyed by the dependent
     * (up) topic, plus id→slug for rendering. grammar_topic.prerequisites is no longer read.
     */
    private record PrereqGraph(Map<Long, List<Long>> downIdsByUpId, Map<Long, String> slugById) {
    }

    private PrereqGraph loadPrereqGraph(List<GrammarTopic> topics) {
        Map<Long, String> slugById = topics.stream()
                .collect(Collectors.toMap(GrammarTopic::getId, GrammarTopic::getSlug));
        Map<Long, List<Long>> downIdsByUpId = new HashMap<>();
        for (GrammarTopicEdge edge : edgeRepository.findByDeleted(0)) {
            downIdsByUpId.computeIfAbsent(edge.getUpTopicId(), key -> new ArrayList<>())
                    .add(edge.getDownTopicId());
        }
        return new PrereqGraph(downIdsByUpId, slugById);
    }

    private GrammarDtos.TopicDto toTopicDto(GrammarTopic topic,
                                            GrammarProgress progress,
                                            PrereqGraph graph,
                                            Map<Long, GrammarProgress> progressById) {
        List<Long> prereqIds = graph.downIdsByUpId().getOrDefault(topic.getId(), List.of());
        List<String> prerequisites = prereqIds.stream()
                .map(graph.slugById()::get)
                .filter(Objects::nonNull)
                .toList();
        boolean prerequisiteMet = prereqIds.stream().allMatch(id -> {
            if (!graph.slugById().containsKey(id)) {
                return true; // unpublished prerequisite: unearnable, so never gate on it
            }
            GrammarProgress prerequisiteProgress = progressById.get(id);
            return prerequisiteProgress != null
                    && prerequisiteProgress.getStatus() == GrammarStatus.MASTERED;
        });
        GrammarStatus status;
        if (progress != null && progress.getStatus() == GrammarStatus.MASTERED) {
            status = GrammarStatus.MASTERED;
        } else if (progress != null && progress.getStatus() == GrammarStatus.IN_PROGRESS) {
            status = GrammarStatus.IN_PROGRESS;
        } else if (prereqIds.isEmpty() || prerequisiteMet) {
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
                topic.getSortOrder(),
                progress == null ? null : progress.getNextReviewAt(),
                topic.getSource()
        );
    }

    /** All grammar rows for one topic in the unified bank, insertion order: seed rows first. */
    private List<Question> topicRows(Long topicId) {
        return questionRepository
                .findByDomainAndTopicIdAndDeletedOrderByIdAsc(Question.DOMAIN_GRAMMAR, topicId, 0);
    }

    private LessonPools buildPools(Long userId, Long topicId, GrammarProgress progress) {
        List<Question> rows = topicRows(topicId);
        List<PoolExercise> seed = rows.stream()
                .filter(row -> SOURCE_SEED.equals(row.getSource()))
                .map(this::toPoolExercise)
                .toList();
        List<PoolExercise> aiPractice = rows.stream()
                .filter(row -> POOL_PRACTICE.equals(row.getPool()) && !SOURCE_SEED.equals(row.getSource()))
                .map(this::toPoolExercise)
                .toList();
        List<PoolExercise> aiBoss = rows.stream()
                .filter(row -> POOL_BOSS.equals(row.getPool()))
                .map(this::toPoolExercise)
                .toList();

        List<PoolExercise> practicePool = new ArrayList<>(seed);
        practicePool.addAll(aiPractice);
        Map<String, Long> lastSeen = lastSeenAttemptIds(userId, topicId);
        Set<String> wrongKeys = attemptRepository.findWrongQuestionKeys(userId, Question.DOMAIN_GRAMMAR, topicId);
        Set<String> excluded = readJson(progress == null ? null : progress.getLastBossIds(),
                new TypeReference<List<String>>() {
                }, List.of()).stream().filter(Objects::nonNull).collect(Collectors.toSet());

        // Practice keeps a fresh shuffle on every load: attempts are low-stakes with
        // immediate feedback, and "再练一组" is expected to reshuffle the window.
        List<PoolExercise> practice = new ArrayList<>(seed.subList(0, Math.min(seed.size(), PRACTICE_SIZE)));
        List<PoolExercise> shuffled = new ArrayList<>(aiPractice);
        shuffled.sort(coldFirst(lastSeen, new Random()));
        int room = PRACTICE_SIZE - practice.size();
        if (room > 0) {
            practice.addAll(shuffled.subList(0, Math.min(room, shuffled.size())));
        }

        List<PoolExercise> boss;
        List<PoolExercise> bossPool;
        List<PoolExercise> bossUniverse;
        Set<String> bossWrongKeys;
        String bossSource;
        if (aiBoss.size() >= BOSS_SIZE) {
            // Wrong answers have already been revealed to the user (practice shows the
            // explanation immediately, boss reveals answers on submit), so pulling them
            // back is review, not answer-farming: they lead the paper, capped so the
            // rest stay fresh. The grading universe widens to include these practice-pool
            // candidates, and the candidate set is monotonic (ever-wrong keys).
            List<PoolExercise> wrongCandidates = practicePool.stream()
                    .filter(item -> wrongKeys.contains(item.id()) && !excluded.contains(item.id()))
                    .toList();
            BossDraw draw = drawBossPaper(userId, aiBoss, wrongCandidates, lastSeen, wrongKeys, progress, excluded);
            boss = draw.paper();
            bossWrongKeys = draw.wrongOnPaper();
            bossPool = List.copyOf(aiBoss);
            bossUniverse = distinctById(aiBoss, wrongCandidates);
            bossSource = SOURCE_AI;
        } else {
            // Offline/demo fallback: the three seed questions stay playable, but a
            // perfect score is required because 2/3 is below the 80% pass line.
            // Rotation is impossible — the pool holds exactly one paper.
            boss = List.copyOf(seed.subList(0, Math.min(seed.size(), PRACTICE_SIZE)));
            bossPool = boss;
            bossUniverse = boss;
            bossWrongKeys = Set.of();
            bossSource = SOURCE_SEED;
        }
        return new LessonPools(practice, boss, List.copyOf(practicePool), bossPool, bossUniverse,
                bossWrongKeys, aiPractice.size(), aiBoss.size(), bossSource);
    }

    private static List<PoolExercise> distinctById(List<PoolExercise> first, List<PoolExercise> second) {
        Map<String, PoolExercise> byId = new LinkedHashMap<>();
        first.forEach(item -> byId.put(item.id(), item));
        second.forEach(item -> byId.putIfAbsent(item.id(), item));
        return List.copyOf(byId.values());
    }

    /** Proficiency signal behind {@code recommendBoss}: every practice-pool question has
     *  been answered correctly at least once, so the fixed set only re-reveals known answers. */
    private boolean practicePoolExhausted(Long userId, Long topicId, List<PoolExercise> practicePool) {
        if (practicePool.isEmpty()) {
            return false;
        }
        Set<String> correctKeys = attemptRepository.findCorrectQuestionKeys(userId, Question.DOMAIN_GRAMMAR, topicId);
        return practicePool.stream().allMatch(item -> correctKeys.contains(item.id()));
    }

    /**
     * Draw one boss paper. Up to {@code WRONG_REVIEW_CAP} slots go to wrong-answer
     * re-practice — boss-pool misses first, then practice-pool misses, most recently
     * attempted first — so a retake targets exactly what the user keeps getting wrong.
     * The remaining slots stay fresh: the latest submitted paper is excluded and the
     * rest go never-answered first. The tie-break shuffle is seeded with
     * (user, topic, lastBossAt), so repeated GETs within one round return the same
     * paper while submitting BOSS (which bumps lastBossAt) rotates to a new one.
     * If the pool shrinks below one fresh paper the previous questions come back —
     * strictly not worse than the old fixed paper.
     */
    private BossDraw drawBossPaper(Long userId, List<PoolExercise> aiBoss, List<PoolExercise> wrongCandidates,
                                   Map<String, Long> lastSeen, Set<String> wrongKeys, GrammarProgress progress,
                                   Set<String> excluded) {
        long roundSeed = Objects.hash(userId, progress == null || progress.getLastBossAt() == null
                ? 0L : progress.getLastBossAt().getEpochSecond());
        Random random = new Random(roundSeed);
        Comparator<PoolExercise> recentWrongFirst = Comparator
                .comparing((PoolExercise item) -> lastSeen.getOrDefault(item.id(), -1L), Comparator.reverseOrder())
                .thenComparing(PoolExercise::id);

        List<PoolExercise> paper = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        Set<String> wrongOnPaper = new HashSet<>();
        aiBoss.stream()
                .filter(item -> wrongKeys.contains(item.id()) && !excluded.contains(item.id()))
                .sorted(recentWrongFirst)
                .limit(WRONG_REVIEW_CAP)
                .forEach(item -> {
                    paper.add(item);
                    picked.add(item.id());
                    wrongOnPaper.add(item.id());
                });
        wrongCandidates.stream()
                .filter(item -> !picked.contains(item.id()))
                .sorted(recentWrongFirst)
                .limit(WRONG_REVIEW_CAP - paper.size())
                .forEach(item -> {
                    paper.add(item);
                    picked.add(item.id());
                    wrongOnPaper.add(item.id());
                });

        aiBoss.stream()
                .filter(item -> !excluded.contains(item.id()) && !picked.contains(item.id()))
                .sorted(coldFirst(lastSeen, random))
                .limit(BOSS_SIZE - paper.size())
                .forEach(item -> {
                    paper.add(item);
                    picked.add(item.id());
                });
        if (paper.size() < BOSS_SIZE) {
            aiBoss.stream()
                    .filter(item -> excluded.contains(item.id()) && !picked.contains(item.id()))
                    .sorted(coldFirst(lastSeen, random))
                    .limit(BOSS_SIZE - paper.size())
                    .forEach(item -> {
                        paper.add(item);
                        picked.add(item.id());
                    });
        }
        return new BossDraw(List.copyOf(paper), Set.copyOf(wrongOnPaper));
    }

    private record BossDraw(List<PoolExercise> paper, Set<String> wrongOnPaper) {
    }

    /** Cold-first ordering: least recent attempt wins, never-attempted is coldest. */
    private Comparator<PoolExercise> coldFirst(Map<String, Long> lastSeen, Random random) {
        return Comparator
                .comparing((PoolExercise item) -> lastSeen.getOrDefault(item.id(), -1L))
                .thenComparing(item -> random.nextDouble());
    }

    private Map<String, Long> lastSeenAttemptIds(Long userId, Long topicId) {
        Map<String, Long> lastSeen = new HashMap<>();
        for (QuestionAttempt attempt : attemptRepository
                .findTop30ByUserIdAndDomainAndTopicIdOrderByIdDesc(userId, Question.DOMAIN_GRAMMAR, topicId)) {
            lastSeen.putIfAbsent(attempt.getQuestionKey(), attempt.getId());
        }
        return lastSeen;
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
        for (QuestionAttempt attempt : attemptRepository
                .findTop30ByUserIdAndDomainAndTopicIdOrderByIdDesc(userId, Question.DOMAIN_GRAMMAR, topicId)) {
            if (attempt.isCorrect() || !seen.add(attempt.getQuestionKey())) {
                continue;
            }
            PoolExercise exercise = byId.get(attempt.getQuestionKey());
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

    private PoolExercise toPoolExercise(Question row) {
        return new PoolExercise(
                row.getQuestionKey(),
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

    private GrammarDtos.ExerciseDto toExerciseDto(PoolExercise exercise, boolean wrongReview) {
        // Options are identified by their text everywhere (grading compares normalized
        // answer text, feedback maps notes by option text), so shuffling the display
        // order is safe and stops the user from memorising "the answer is the 2nd box".
        List<String> options = new ArrayList<>(exercise.options());
        Collections.shuffle(options);
        return new GrammarDtos.ExerciseDto(
                exercise.id(),
                exercise.type(),
                exercise.prompt(),
                options,
                exercise.skill(),
                exercise.level(),
                exercise.source(),
                wrongReview
        );
    }

    private Optional<PoolExercise> validateGenerated(GrammarGenDtos.GenExercise raw, String mode) {
        if (raw == null) {
            return Optional.empty();
        }
        String type = raw.type() == null ? TYPE_MCQ : raw.type().trim().toUpperCase(Locale.ROOT);
        String prompt = truncate(raw.prompt(), 500);
        if (prompt.isBlank()) {
            return Optional.empty();
        }
        String explanation = truncate(raw.explanation(), 2000);
        Integer level = raw.level() == null ? null : Math.max(1, Math.min(4, raw.level()));
        if (QuestionType.SUBJECTIVE.contains(type)) {
            return validateGeneratedSubjective(raw, type, prompt, explanation, level, mode);
        }
        if (!TYPE_MCQ.equals(type)) {
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
        if (explanation.isBlank()) {
            return Optional.empty();
        }
        List<String> notes = raw.optionNotes() == null ? List.of() : raw.optionNotes().stream()
                .map(note -> truncate(note, 500))
                .toList();
        if (notes.size() != options.size() || notes.stream().anyMatch(String::isBlank)) {
            return Optional.empty();
        }
        return Optional.of(new PoolExercise(
                "ai-" + UUID.randomUUID(),
                TYPE_MCQ,
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

    /**
     * Open (typed-answer) items need only a prompt plus a model answer: the model
     * answer feeds the exercise-judge call and the wrong-answer review, so an item
     * without one is unusable. Options must be empty — a "TRANSFORM" that still
     * carries 4 options is the model hedging, not the item we asked for.
     */
    private Optional<PoolExercise> validateGeneratedSubjective(GrammarGenDtos.GenExercise raw, String type,
                                                               String prompt, String explanation,
                                                               Integer level, String mode) {
        if (raw.options() != null && !raw.options().isEmpty()) {
            return Optional.empty();
        }
        String reference = truncate(raw.answer(), 800);
        if (reference.isBlank() || explanation.isBlank()) {
            return Optional.empty();
        }
        String skill = normalizeSkill(raw.skill());
        return Optional.of(new PoolExercise(
                "ai-" + UUID.randomUUID(),
                type,
                prompt,
                List.of(),
                reference,
                explanation,
                List.of(),
                skill == null ? "use" : skill,
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
            Question entity = new Question();
            entity.setQuestionKey(candidate.id());
            entity.setDomain(Question.DOMAIN_GRAMMAR);
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
            questionRepository.save(entity);
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

    private String concatPrompts(List<PoolExercise> seed, List<Question> existing) {
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
            List<PoolExercise> bossPool,
            /** What a BOSS submission may contain: boss pool + wrong-review candidates. */
            List<PoolExercise> bossUniverse,
            /** Boss paper ids flagged as wrong-answer re-practice for the UI pill. */
            Set<String> bossWrongKeys,
            int aiPracticeCount,
            int aiBossCount,
            String bossSource
    ) {
    }

    /** Unified in-memory question shape: both SEED and AI bank rows normalise into this. */
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

}
