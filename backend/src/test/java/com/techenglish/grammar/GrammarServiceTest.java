package com.techenglish.grammar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.ai.AiDtos;
import com.techenglish.ai.AiGateway;
import com.techenglish.common.ApiException;
import com.techenglish.identity.LearnerProfileRepository;
import com.techenglish.question.Question;
import com.techenglish.question.QuestionAttempt;
import com.techenglish.question.QuestionAttemptRepository;
import com.techenglish.question.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the boss rotation contract: a retake must not reuse the revealed paper,
 *  submissions are graded by pool membership, and mastery never rolls back.
 *  Rows come from the unified question bank (domain GRAMMAR, SEED/AI sources). */
class GrammarServiceTest {

    private static final Question SEED_1 = seedRow(1);
    private static final Question SEED_2 = seedRow(2);
    private static final Question SEED_3 = seedRow(3);

    private final GrammarTopicRepository topicRepository = mock(GrammarTopicRepository.class);
    private final GrammarTopicEdgeRepository edgeRepository = mock(GrammarTopicEdgeRepository.class);
    private final GrammarLessonRepository lessonRepository = mock(GrammarLessonRepository.class);
    private final GrammarProgressRepository progressRepository = mock(GrammarProgressRepository.class);
    private final QuestionAttemptRepository attemptRepository = mock(QuestionAttemptRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final LearnerProfileRepository profileRepository = mock(LearnerProfileRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** submit() persists through this template; the test runs the callback inline. */
    private final TransactionTemplate transactionTemplate = passThroughTransactions();

    private static TransactionTemplate passThroughTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        return template;
    }

    private final GrammarService service = new GrammarService(topicRepository, edgeRepository,
            lessonRepository, progressRepository, attemptRepository, questionRepository,
            profileRepository, aiGateway, objectMapper, transactionTemplate);

    private final GrammarTopic topic = topic();
    private final GrammarLesson lesson = lesson();
    private final GrammarProgress progress = new GrammarProgress();

    private static GrammarTopic topic() {
        GrammarTopic entity = new GrammarTopic();
        entity.setId(1L);
        entity.setSlug("articles");
        entity.setTitle("Articles");
        entity.setWorld("Foundations");
        entity.setWorldOrder(1);
        entity.setCefrLevel("A2");
        entity.setSummary("a / an / the");
        entity.setSortOrder(1);
        return entity;
    }

    private static GrammarLesson lesson() {
        GrammarLesson entity = new GrammarLesson();
        entity.setId(1L);
        entity.setContentMd("rules");
        entity.setExamplesJson("[]");
        entity.setExercisesJson("[]"); // legacy archive: pools now come from the question table
        return entity;
    }

    /** Materialised seed question (what QuestionBackfillRunner / ContentSeeder produce). */
    private static Question seedRow(int i) {
        Question entity = new Question();
        entity.setId((long) (200 + i));
        entity.setQuestionKey("seed-" + i);
        entity.setDomain(Question.DOMAIN_GRAMMAR);
        entity.setTopicId(1L);
        entity.setPool("PRACTICE");
        entity.setType("MCQ");
        entity.setPrompt("Q" + i);
        entity.setOptionsJson("[\"a\",\"b\"]");
        entity.setAnswer("a");
        entity.setExplanation("x");
        entity.setOptionNotesJson("[]");
        entity.setSource("SEED");
        return entity;
    }

    private static Question aiBoss(int i) {
        Question entity = new Question();
        entity.setId((long) i);
        entity.setQuestionKey("ai-b" + i);
        entity.setDomain(Question.DOMAIN_GRAMMAR);
        entity.setTopicId(1L);
        entity.setPool("BOSS");
        entity.setType("MCQ");
        entity.setPrompt("Boss question " + i);
        entity.setOptionsJson("[\"right\",\"wrong\"]");
        entity.setAnswer("right");
        entity.setExplanation("because");
        entity.setOptionNotesJson("[\"ok\",\"nope\"]");
        entity.setSource("AI");
        return entity;
    }

    private static Question aiPractice(int i) {
        Question entity = new Question();
        entity.setId((long) (100 + i));
        entity.setQuestionKey("ai-p" + i);
        entity.setDomain(Question.DOMAIN_GRAMMAR);
        entity.setTopicId(1L);
        entity.setPool("PRACTICE");
        entity.setType("MCQ");
        entity.setPrompt("Practice question " + i);
        entity.setOptionsJson("[\"right\",\"wrong\"]");
        entity.setAnswer("right");
        entity.setExplanation("because");
        entity.setOptionNotesJson("[\"ok\",\"nope\"]");
        entity.setSource("AI");
        return entity;
    }

    private void stubLessonPath(List<Question> rows, List<GrammarProgress> progressRows) {
        when(topicRepository.findBySlugAndPublishedTrue("articles")).thenReturn(Optional.of(topic));
        when(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()).thenReturn(List.of(topic));
        when(lessonRepository.findByTopicId(1L)).thenReturn(Optional.of(lesson));
        when(questionRepository.findByDomainAndTopicIdAndDeletedOrderByIdAsc(
                Question.DOMAIN_GRAMMAR, 1L, 0)).thenReturn(rows);
        when(attemptRepository.findTop30ByUserIdAndDomainAndTopicIdOrderByIdDesc(
                1L, Question.DOMAIN_GRAMMAR, 1L)).thenReturn(List.of());
        when(aiGateway.status()).thenReturn(new AiDtos.AiStatusResponse("deepseek", true, "model-x"));
        when(edgeRepository.findByDeleted(0)).thenReturn(List.of());
        when(progressRepository.findByUserId(1L)).thenReturn(progressRows);
        when(progressRepository.findByUserIdAndTopicId(1L, 1L)).thenReturn(Optional.of(progress));
        when(progressRepository.save(any(GrammarProgress.class))).thenAnswer(call -> call.getArgument(0));
        when(attemptRepository.save(any(QuestionAttempt.class))).thenAnswer(call -> call.getArgument(0));
        progress.setUserId(1L);
        progress.setTopicId(1L);
    }

    /** The three seed questions followed by the given AI rows, as the bank stores them. */
    private static List<Question> bankRows(Question... ai) {
        return Stream.concat(Stream.of(SEED_1, SEED_2, SEED_3), Stream.of(ai)).toList();
    }

    private List<String> bossIds(GrammarDtos.LessonResponse response) {
        return response.boss().stream().map(GrammarDtos.ExerciseDto::id).toList();
    }

    private GrammarDtos.AttemptsRequest bossRequest(List<String> ids, String answer) {
        return new GrammarDtos.AttemptsRequest("BOSS", ids.stream()
                .map(id -> new GrammarDtos.AttemptAnswer(id, answer))
                .toList());
    }

    @Test
    void retakeRotatesAwayFromTheRevealedPaper() {
        stubLessonPath(new ArrayList<>(List.of(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5),
                aiBoss(6), aiBoss(7), aiBoss(8))), List.of(progress));

        List<String> firstPaper = bossIds(service.lesson(1L, "articles"));
        assertThat(firstPaper).hasSize(5);

        service.submit(1L, "articles", bossRequest(firstPaper, "wrong"));

        List<String> secondPaper = bossIds(service.lesson(1L, "articles"));
        assertThat(secondPaper).hasSize(5);
        Set<String> unseen = secondPaper.stream()
                .filter(id -> !firstPaper.contains(id))
                .collect(Collectors.toSet());
        // Pool of 8 minus the 5 revealed questions: at least 3 genuinely fresh items.
        assertThat(unseen).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void bossSubmissionIsGradedByPoolMembershipAndPassesAt80() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5),
                aiBoss(6), aiBoss(7), aiBoss(8)), List.of());
        List<String> paper = bossIds(service.lesson(1L, "articles"));

        List<GrammarDtos.AttemptAnswer> answers = new ArrayList<>();
        for (int i = 0; i < paper.size(); i++) {
            answers.add(new GrammarDtos.AttemptAnswer(paper.get(i), i == 0 ? "wrong" : "right"));
        }
        GrammarDtos.AttemptsResponse response = service.submit(1L, "articles",
                new GrammarDtos.AttemptsRequest("BOSS", answers));

        assertThat(response.total()).isEqualTo(5);
        assertThat(response.correct()).isEqualTo(4);
        assertThat(response.score()).isEqualTo(80.0);
        assertThat(response.passed()).isTrue();
        assertThat(response.status()).isEqualTo("MASTERED");
        assertThat(progress.getLastBossIds()).contains(paper.get(0));
        assertThat(progress.getLastBossAt()).isNotNull();
    }

    @Test
    void bossRejectsForeignIdsAndIncompletePapers() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5)), List.of());

        ApiException foreign = catchThrowableOfType(
                () -> service.submit(1L, "articles", bossRequest(List.of("ai-b1", "ai-b2", "ai-b3", "ai-b4", "nope"), "right")),
                ApiException.class);
        assertThat(foreign.getCode()).isEqualTo("EXERCISE_SET_CHANGED");

        ApiException tooFew = catchThrowableOfType(
                () -> service.submit(1L, "articles", bossRequest(List.of("ai-b1", "ai-b2", "ai-b3", "ai-b4"), "right")),
                ApiException.class);
        assertThat(tooFew.getCode()).isEqualTo("INCOMPLETE_PRACTICE");
    }

    @Test
    void masteredNodeDoesNotRollBackOnAFailedBoss() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5)), List.of(progress));
        progress.setStatus(GrammarStatus.MASTERED);
        progress.setMasteryScore(new java.math.BigDecimal("100.00"));
        progress.setBossScore(new java.math.BigDecimal("100.00"));

        List<String> paper = bossIds(service.lesson(1L, "articles"));
        GrammarDtos.AttemptsResponse response = service.submit(1L, "articles", bossRequest(paper, "wrong"));

        assertThat(response.passed()).isFalse();
        assertThat(response.status()).isEqualTo("MASTERED");
        assertThat(response.unlockedNext()).isFalse();
        assertThat(progress.getMasteryScore().doubleValue()).isEqualTo(100.0);
    }

    @Test
    void seedBossKeepsAllThreeQuestionsAndNeedsAPerfectRun() {
        stubLessonPath(new ArrayList<>(List.of(SEED_1, SEED_2, SEED_3)), List.of());

        GrammarDtos.LessonResponse lesson = service.lesson(1L, "articles");
        assertThat(lesson.pool().bossSource()).isEqualTo("SEED");
        assertThat(lesson.boss()).hasSize(3);

        // Seed answers are "a": 2/3 (66.7) must fail, 3/3 passes.
        GrammarDtos.AttemptsResponse twoOfThree = service.submit(1L, "articles",
                new GrammarDtos.AttemptsRequest("BOSS", List.of(
                        new GrammarDtos.AttemptAnswer("seed-1", "a"),
                        new GrammarDtos.AttemptAnswer("seed-2", "a"),
                        new GrammarDtos.AttemptAnswer("seed-3", "b"))));
        assertThat(twoOfThree.score()).isEqualTo(66.7);
        assertThat(twoOfThree.passed()).isFalse();

        GrammarDtos.AttemptsResponse perfect = service.submit(1L, "articles",
                bossRequest(List.of("seed-1", "seed-2", "seed-3"), "a"));
        assertThat(perfect.passed()).isTrue();
    }

    @Test
    void generationNeededStaysTrueUntilTheBossPoolReachesEight() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5)), List.of());
        GrammarDtos.LessonResponse lesson = service.lesson(1L, "articles");
        assertThat(lesson.pool().generationNeeded()).isTrue();
        // A 5-question AI boss pool is already playable.
        assertThat(lesson.boss()).hasSize(5);
    }

    @Test
    void bossDrawEndpointReturnsAFreshRotatedPaper() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5),
                aiBoss(6), aiBoss(7), aiBoss(8)), List.of(progress));
        List<String> first = service.drawBoss(1L, "articles").exercises().stream()
                .map(GrammarDtos.ExerciseDto::id).toList();
        service.submit(1L, "articles", bossRequest(first, "wrong"));

        List<String> second = service.drawBoss(1L, "articles").exercises().stream()
                .map(GrammarDtos.ExerciseDto::id).toList();
        assertThat(second).hasSize(5);
        assertThat(second).isNotEqualTo(first);
    }

    private static GrammarTopic prereqTopic() {
        GrammarTopic entity = new GrammarTopic();
        entity.setId(2L);
        entity.setSlug("sentence-basics");
        entity.setTitle("Basics");
        entity.setWorld("Foundations");
        entity.setWorldOrder(1);
        entity.setCefrLevel("A1");
        entity.setSummary("sentence parts");
        entity.setSortOrder(0);
        return entity;
    }

    private void stubTreeStaging(List<GrammarTopic> published, List<GrammarTopicEdge> edges,
                                 List<GrammarProgress> progressRows) {
        when(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()).thenReturn(published);
        when(edgeRepository.findByDeleted(0)).thenReturn(edges);
        when(progressRepository.findByUserId(1L)).thenReturn(progressRows);
    }

    @Test
    void unlockFollowsTheEdgeTableNotTheJsonColumn() {
        stubLessonPath(new ArrayList<>(List.of(SEED_1, SEED_2, SEED_3)), List.of());
        GrammarTopic prereq = prereqTopic();
        stubTreeStaging(List.of(prereq, topic), List.of(GrammarTopicEdge.prerequisite(1L, 2L)), List.of());
        when(topicRepository.findBySlugAndPublishedTrue("articles")).thenReturn(Optional.of(topic));

        // The JSON prerequisites column is empty, yet the edge locks the node.
        ApiException locked = catchThrowableOfType(() -> service.lesson(1L, "articles"), ApiException.class);
        assertThat(locked.getCode()).isEqualTo("TOPIC_LOCKED");

        GrammarProgress prereqProgress = new GrammarProgress();
        prereqProgress.setUserId(1L);
        prereqProgress.setTopicId(2L);
        prereqProgress.setStatus(GrammarStatus.MASTERED);
        when(progressRepository.findByUserId(1L)).thenReturn(List.of(prereqProgress));
        assertThat(service.lesson(1L, "articles").topic().slug()).isEqualTo("articles");
    }

    @Test
    void treeReturnsNodesWithStatusAndEdgesWithSlugs() {
        GrammarTopic prereq = prereqTopic();
        GrammarProgress prereqProgress = new GrammarProgress();
        prereqProgress.setUserId(1L);
        prereqProgress.setTopicId(2L);
        prereqProgress.setStatus(GrammarStatus.MASTERED);
        prereqProgress.setMasteryScore(new java.math.BigDecimal("100.00"));
        stubTreeStaging(List.of(prereq, topic), List.of(GrammarTopicEdge.prerequisite(1L, 2L)),
                List.of(prereqProgress));

        GrammarDtos.TreeResponse tree = service.tree(1L);

        assertThat(tree.nodes()).hasSize(2);
        assertThat(tree.edges()).singleElement()
                .extracting(GrammarDtos.TreeEdgeDto::upSlug, GrammarDtos.TreeEdgeDto::downSlug)
                .containsExactly("articles", "sentence-basics");
        GrammarDtos.TreeNodeDto article = tree.nodes().stream()
                .filter(node -> node.slug().equals("articles")).findFirst().orElseThrow();
        assertThat(article.status()).isEqualTo("AVAILABLE");
        assertThat(article.source()).isEqualTo("SEED");
        assertThat(tree.overallMastery()).isEqualTo(50.0);
    }

    @Test
    void createTopicPersistsNodeLessonAndPrerequisiteEdges() {
        GrammarTopic prereq = prereqTopic();
        List<GrammarTopic> published = new ArrayList<>(List.of(prereq));
        List<GrammarTopicEdge> edges = new ArrayList<>();
        when(topicRepository.existsBySlugAndDeleted("gerunds", 0)).thenReturn(false);
        when(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()).thenReturn(published);
        when(topicRepository.save(any(GrammarTopic.class))).thenAnswer(call -> {
            GrammarTopic entity = call.getArgument(0);
            entity.setId(7L);
            published.add(entity);
            return entity;
        });
        when(edgeRepository.findByDeleted(0)).thenReturn(edges);
        when(edgeRepository.save(any(GrammarTopicEdge.class))).thenAnswer(call -> {
            edges.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(lessonRepository.save(any(GrammarLesson.class))).thenAnswer(call -> call.getArgument(0));
        when(progressRepository.findByUserId(1L)).thenReturn(List.of());

        GrammarDtos.TreeNodeDto node = service.createTopic(1L, new GrammarDtos.TopicCreateRequest(
                "Gerunds", "动名词", "doing 作主语或宾语。", "b1", "Foundations", null,
                List.of("sentence-basics"), null, null));

        assertThat(node.slug()).isEqualTo("gerunds");
        assertThat(node.source()).isEqualTo("USER");
        assertThat(node.cefrLevel()).isEqualTo("B1");
        assertThat(node.worldOrder()).isEqualTo(1); // joined the existing Foundations world
        assertThat(node.status()).isEqualTo("LOCKED"); // prerequisite not mastered yet
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).getUpTopicId()).isEqualTo(7L);
        assertThat(edges.get(0).getDownTopicId()).isEqualTo(2L);

        ArgumentCaptor<GrammarLesson> lessonCaptor = ArgumentCaptor.forClass(GrammarLesson.class);
        verify(lessonRepository).save(lessonCaptor.capture());
        assertThat(lessonCaptor.getValue().getContentMd()).contains("讲义待补充");
        assertThat(lessonCaptor.getValue().getExercisesJson()).isEqualTo("[]");
    }

    @Test
    void createTopicRejectsDuplicateSlugAndUnknownPrerequisite() {
        when(topicRepository.existsBySlugAndDeleted("articles", 0)).thenReturn(true);
        ApiException duplicate = catchThrowableOfType(() -> service.createTopic(1L,
                new GrammarDtos.TopicCreateRequest("articles", "dup", "s", null, null, null,
                        null, null, null)), ApiException.class);
        assertThat(duplicate.getCode()).isEqualTo("SLUG_EXISTS");

        when(topicRepository.existsBySlugAndDeleted("gerunds", 0)).thenReturn(false);
        when(topicRepository.findByPublishedTrueOrderByWorldOrderAscSortOrderAsc()).thenReturn(List.of(topic));
        ApiException unknown = catchThrowableOfType(() -> service.createTopic(1L,
                new GrammarDtos.TopicCreateRequest("gerunds", "动名词", "s", null, null, null,
                        List.of("no-such-topic"), null, null)), ApiException.class);
        assertThat(unknown.getCode()).isEqualTo("UNKNOWN_PREREQUISITE");
    }

    // ---------- wrong-answer re-practice + proficiency-gated boss entry ----------

    private static QuestionAttempt attempt(long id, String key, boolean correct) {
        QuestionAttempt entity = new QuestionAttempt();
        entity.setId(id);
        entity.setUserId(1L);
        entity.setDomain(Question.DOMAIN_GRAMMAR);
        entity.setTopicId(1L);
        entity.setQuestionKey(key);
        entity.setAnswer("x");
        entity.setCorrect(correct);
        return entity;
    }

    @Test
    void bossPaperLeadsWithWrongAnswers() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5),
                aiBoss(6), aiBoss(7), aiBoss(8)), List.of());
        when(attemptRepository.findWrongQuestionKeys(1L, Question.DOMAIN_GRAMMAR, 1L))
                .thenReturn(Set.of("ai-b7", "seed-1"));
        when(attemptRepository.findTop30ByUserIdAndDomainAndTopicIdOrderByIdDesc(
                1L, Question.DOMAIN_GRAMMAR, 1L)).thenReturn(List.of(
                attempt(200L, "ai-b7", false),
                attempt(100L, "seed-1", false)));

        List<GrammarDtos.ExerciseDto> paper = service.lesson(1L, "articles").boss();

        // Boss-pool misses first, then practice-pool misses, then fresh questions.
        assertThat(paper).extracting(GrammarDtos.ExerciseDto::id).startsWith("ai-b7", "seed-1");
        assertThat(paper).extracting(GrammarDtos.ExerciseDto::wrongReview)
                .containsExactly(true, true, false, false, false);
    }

    @Test
    void bossSubmissionAcceptsWrongReviewItemFromPracticePool() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5),
                aiBoss(6), aiBoss(7), aiBoss(8)), List.of());
        when(attemptRepository.findWrongQuestionKeys(1L, Question.DOMAIN_GRAMMAR, 1L))
                .thenReturn(Set.of("seed-1"));
        when(attemptRepository.findTop30ByUserIdAndDomainAndTopicIdOrderByIdDesc(
                1L, Question.DOMAIN_GRAMMAR, 1L)).thenReturn(List.of(
                attempt(50L, "seed-1", false)));

        List<String> paper = bossIds(service.lesson(1L, "articles"));
        assertThat(paper).contains("seed-1");
        // Before this feature seed-1 would fail with EXERCISE_SET_CHANGED:
        // wrong-review items are not in the boss pool but belong on the paper.
        GrammarDtos.AttemptsResponse response = service.submit(1L, "articles", bossRequest(paper, "right"));
        assertThat(response.total()).isEqualTo(5);
    }

    @Test
    void recommendBossTurnsOnWhenThePracticePoolIsExhausted() {
        stubLessonPath(bankRows(
                aiBoss(1), aiBoss(2), aiBoss(3), aiBoss(4), aiBoss(5),
                aiPractice(1), aiPractice(2), aiPractice(3), aiPractice(4), aiPractice(5)), List.of());

        assertThat(service.lesson(1L, "articles").recommendBoss()).isFalse();

        when(attemptRepository.findCorrectQuestionKeys(1L, Question.DOMAIN_GRAMMAR, 1L)).thenReturn(Set.of(
                "seed-1", "seed-2", "seed-3", "ai-p1", "ai-p2", "ai-p3", "ai-p4", "ai-p5"));
        assertThat(service.lesson(1L, "articles").recommendBoss()).isTrue();
    }

    @Test
    void masteredNodeAlwaysRecommendsBoss() {
        stubLessonPath(new ArrayList<>(List.of(SEED_1, SEED_2, SEED_3)), List.of(progress)); // SEED fallback boss source
        progress.setStatus(GrammarStatus.MASTERED);

        assertThat(service.lesson(1L, "articles").recommendBoss()).isTrue();
    }
}
