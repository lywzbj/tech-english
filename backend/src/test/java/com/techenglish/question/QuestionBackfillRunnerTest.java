package com.techenglish.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.grammar.GrammarLesson;
import com.techenglish.grammar.GrammarLessonRepository;
import com.techenglish.grammar.GrammarTopic;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Seed JSON materialisation + legacy key remapping into the unified bank. */
class QuestionBackfillRunnerTest {

    private static final String SEEDS = """
            [
              {"id":"q1","type":"MCQ","prompt":"Q1","options":["a","b"],"answer":"a","explanation":"x"},
              {"id":"q2","type":null,"prompt":"Q2","options":["a","b"],"answer":"a","explanation":"x"}
            ]""";

    private final GrammarLessonRepository lessonRepository = mock(GrammarLessonRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final QuestionBackfillRunner runner = new QuestionBackfillRunner(
            lessonRepository, questionRepository, jdbcTemplate, objectMapper);

    private static GrammarLesson lesson(String exercisesJson) {
        GrammarTopic topic = new GrammarTopic();
        topic.setId(1L);
        GrammarLesson lesson = new GrammarLesson();
        lesson.setId(1L);
        lesson.setTopic(topic);
        lesson.setExercisesJson(exercisesJson);
        return lesson;
    }

    private static Question existing(String key, String source) {
        Question row = new Question();
        row.setQuestionKey(key);
        row.setSource(source);
        return row;
    }

    @Test
    void materialisesSeedRowsAndRemapsLegacyKeys() throws Exception {
        when(lessonRepository.findAll()).thenReturn(List.of(lesson(SEEDS)));
        when(questionRepository.findByDomainAndTopicIdAndDeletedOrderByIdAsc(
                Question.DOMAIN_GRAMMAR, 1L, 0)).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(5L));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("[\"q1\",\"ai-x\"]");

        runner.run(null);

        ArgumentCaptor<Question> saved = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Question::getQuestionKey)
                .containsExactly("seed-1-q1", "seed-1-q2");
        assertThat(saved.getAllValues()).allSatisfy(row -> {
            assertThat(row.getDomain()).isEqualTo(Question.DOMAIN_GRAMMAR);
            assertThat(row.getSource()).isEqualTo("SEED");
            assertThat(row.getPool()).isEqualTo("PRACTICE");
        });
        // type null in the JSON defaults to MCQ.
        assertThat(saved.getAllValues().get(1).getType()).isEqualTo("MCQ");

        verify(jdbcTemplate).update(contains("UPDATE question_attempt"),
                eq("seed-1-q1"), eq(Question.DOMAIN_GRAMMAR), eq(1L), eq("q1"));
        verify(jdbcTemplate).update(contains("UPDATE question_attempt"),
                eq("seed-1-q2"), eq(Question.DOMAIN_GRAMMAR), eq(1L), eq("q2"));
        verify(jdbcTemplate).update(contains("grammar_progress SET last_boss_ids"),
                contains("seed-1-q1"), eq(5L));
    }

    @Test
    void rerunsAreNoOpsWhenRowsAlreadyExist() {
        when(lessonRepository.findAll()).thenReturn(List.of(lesson(SEEDS)));
        when(questionRepository.findByDomainAndTopicIdAndDeletedOrderByIdAsc(
                Question.DOMAIN_GRAMMAR, 1L, 0)).thenReturn(List.of(
                existing("seed-1-q1", "SEED"), existing("seed-1-q2", "SEED")));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        runner.run(null);

        verify(questionRepository, never()).save(any());
    }

    @Test
    void blankOrArchivedOnlyJsonIsSkipped() {
        when(lessonRepository.findAll()).thenReturn(List.of(lesson("[]")));

        runner.run(null);

        verify(questionRepository, never()).save(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
