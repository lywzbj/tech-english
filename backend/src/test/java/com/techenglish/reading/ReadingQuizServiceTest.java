package com.techenglish.reading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.ai.AiGateway;
import com.techenglish.ai.QuizDtos;
import com.techenglish.question.Question;
import com.techenglish.question.QuestionAttempt;
import com.techenglish.question.QuestionAttemptRepository;
import com.techenglish.question.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Generated self-test questions live in the unified bank; grading writes the shared
 *  attempt ledger instead of writing back onto the question row. */
class ReadingQuizServiceTest {

    private final ReadingArticleRepository articleRepository = mock(ReadingArticleRepository.class);
    private final ReadingQuizSetRepository setRepository = mock(ReadingQuizSetRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final QuestionAttemptRepository attemptRepository = mock(QuestionAttemptRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReadingQuizService service = new ReadingQuizService(
            articleRepository, setRepository, questionRepository, attemptRepository, aiGateway, objectMapper);

    private static QuizDtos.GenQuestion gen(String prompt, String answer) {
        return new QuizDtos.GenQuestion("MCQ", prompt,
                List.of("A. alpha", "B. beta", "C. gamma", "D. delta"),
                answer, "because", "detail", null);
    }

    private static ReadingArticle article() {
        ReadingArticle entity = new ReadingArticle();
        entity.setId(1L);
        entity.setTitle("Quiet Work");
        entity.setCefrLevel("B2");
        entity.setContentMd("Some article body about async work.");
        return entity;
    }

    private static ReadingQuizSet quizSet(boolean submitted) {
        ReadingQuizSet set = new ReadingQuizSet();
        set.setId(9L);
        set.setUserId(1L);
        set.setArticleId(1L);
        set.setDifficulty("NORMAL");
        set.setTotalCount(2);
        set.setSubmittedAt(submitted ? Instant.now() : null);
        if (submitted) {
            set.setCorrectCount(1);
        }
        return set;
    }

    private static Question bankRow(long id, String key, String answerLetter) {
        Question row = new Question();
        row.setId(id);
        row.setQuestionKey(key);
        row.setDomain(Question.DOMAIN_READING);
        row.setArticleId(1L);
        row.setQuizSetId(9L);
        row.setType("MCQ");
        row.setSource("AI");
        row.setPrompt("P" + id);
        row.setOptionsJson("[\"A. alpha\",\"B. beta\",\"C. gamma\",\"D. delta\"]");
        row.setAnswer(answerLetter);
        row.setExplanation("because");
        row.setSortOrder((int) id);
        return row;
    }

    @Test
    void generateStoresQuestionsInTheUnifiedBank() {
        when(articleRepository.findById(1L)).thenReturn(java.util.Optional.of(article()));
        when(aiGateway.generateQuestions(anyLong(), any())).thenReturn(new QuizDtos.QuestionGenResponse(
                "deepseek", "model-x", "question-gen/v2", List.of(gen("Q1", "A"), gen("Q2", "C"))));
        when(setRepository.save(any(ReadingQuizSet.class))).thenAnswer(call -> {
            ReadingQuizSet set = call.getArgument(0);
            set.setId(9L);
            return set;
        });

        QuizDtos.QuizGenerateResponse response = service.generate(1L, 1L,
                new QuizDtos.QuizGenerateRequest("NORMAL", "Sec", "section text"));

        ArgumentCaptor<List<Question>> saved = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue()).allSatisfy(row -> {
            assertThat(row.getDomain()).isEqualTo(Question.DOMAIN_READING);
            assertThat(row.getQuizSetId()).isEqualTo(9L);
            assertThat(row.getSource()).isEqualTo("AI");
        });
        // Answers frozen to option letters; provenance copied from the gateway response.
        assertThat(saved.getValue()).extracting(Question::getAnswer).containsExactly("A", "C");
        assertThat(saved.getValue().get(0).getPromptVersion()).isEqualTo("question-gen/v2");
        assertThat(response.set().questions()).hasSize(2);
    }

    @Test
    void submitWritesAttemptRowsToTheSharedLedger() {
        Question q1 = bankRow(11L, "quiz-11", "A");
        Question q2 = bankRow(12L, "quiz-12", "C");
        when(setRepository.findById(9L)).thenReturn(java.util.Optional.of(quizSet(false)));
        when(questionRepository.findByDomainAndQuizSetIdOrderBySortOrderAsc(
                Question.DOMAIN_READING, 9L)).thenReturn(List.of(q1, q2));

        QuizDtos.QuizSubmitResponse response = service.submit(1L, 9L, new QuizDtos.QuizSubmitRequest(List.of(
                new QuizDtos.QuizAnswerDto(11L, "A"),
                new QuizDtos.QuizAnswerDto(12L, "D. delta"))));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.correct()).isEqualTo(1);
        ArgumentCaptor<QuestionAttempt> saved = ArgumentCaptor.forClass(QuestionAttempt.class);
        verify(attemptRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        QuestionAttempt wrong = saved.getAllValues().get(1);
        assertThat(wrong.getDomain()).isEqualTo(Question.DOMAIN_READING);
        assertThat(wrong.getArticleId()).isEqualTo(1L);
        assertThat(wrong.getQuestionKey()).isEqualTo("quiz-12");
        assertThat(wrong.getAnswer()).isEqualTo("D"); // echoed option text resolved to a letter
        assertThat(wrong.isCorrect()).isFalse();
        // No more answer write-back onto question rows.
        verify(questionRepository, never()).saveAll(any());
    }

    @Test
    void resubmitReplaysStoredAttemptsWithoutRewriting() {
        Question q1 = bankRow(11L, "quiz-11", "A");
        Question q2 = bankRow(12L, "quiz-12", "C");
        when(setRepository.findById(9L)).thenReturn(java.util.Optional.of(quizSet(true)));
        when(questionRepository.findByDomainAndQuizSetIdOrderBySortOrderAsc(
                Question.DOMAIN_READING, 9L)).thenReturn(List.of(q1, q2));
        when(attemptRepository.findByUserIdAndDomainAndQuestionKeyIn(eq(1L), eq(Question.DOMAIN_READING), any()))
                .thenReturn(List.of(attempt("quiz-11", "A", true, 1L), attempt("quiz-12", "B", false, 2L)));

        QuizDtos.QuizSubmitResponse response = service.submit(1L, 9L, new QuizDtos.QuizSubmitRequest(List.of(
                new QuizDtos.QuizAnswerDto(11L, "B"), // ignored: already graded
                new QuizDtos.QuizAnswerDto(12L, "C"))));

        assertThat(response.correct()).isEqualTo(1);
        assertThat(response.items()).extracting(QuizDtos.QuizResultItem::yourAnswer)
                .containsExactly("A", "B");
        verify(attemptRepository, never()).save(any());
    }

    private static QuestionAttempt attempt(String key, String answer, boolean correct, long id) {
        QuestionAttempt entity = new QuestionAttempt();
        entity.setId(id);
        entity.setUserId(1L);
        entity.setDomain(Question.DOMAIN_READING);
        entity.setArticleId(1L);
        entity.setQuestionKey(key);
        entity.setAnswer(answer);
        entity.setCorrect(correct);
        return entity;
    }
}
