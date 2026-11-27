package com.techenglish.reading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.ai.AiGateway;
import com.techenglish.ai.QuizDtos;
import com.techenglish.common.ApiException;
import com.techenglish.question.Question;
import com.techenglish.question.QuestionAttempt;
import com.techenglish.question.QuestionAttemptRepository;
import com.techenglish.question.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-drawer AI reading self-test. Each generate call asks the model for single-choice
 * questions about the section the learner is reading, validates the output defensively
 * (models drift: wrong types, 3 options, answer-as-full-text), then archives the set
 * with (user, article, section) provenance. Generated questions are ordinary rows in
 * the unified question bank (domain=READING, quizSetId set); grading appends rows to
 * the shared {@link QuestionAttempt} ledger, so quiz answers count toward per-question
 * history just like grammar and static-reading attempts. Resubmitting a graded set
 * re-reports the stored result unchanged.
 */
@Service
@RequiredArgsConstructor
public class ReadingQuizService {

    public static final int QUESTIONS_PER_SET = 5;
    /** Fewer surviving questions than this means the model output is not a usable quiz. */
    private static final int MIN_QUIZABLE_QUESTIONS = 2;
    /** Fallback when the learner's section cannot be sliced (short untitled articles). */
    private static final int MAX_FULL_TEXT_CHARS = 60000;
    private static final List<String> DIFFICULTIES = List.of("EASY", "NORMAL", "HARD");

    private final ReadingArticleRepository articleRepository;
    private final ReadingQuizSetRepository setRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAttemptRepository attemptRepository;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuizDtos.QuizGenerateResponse generate(Long userId, Long articleId,
                                                  QuizDtos.QuizGenerateRequest request) {
        ReadingArticle article = requireArticle(articleId);
        String difficulty = normalizeDifficulty(request.difficulty());

        String section = request.sectionText() == null ? "" : request.sectionText().trim();
        boolean wholeArticle = section.isEmpty();
        if (wholeArticle) {
            section = article.getContentMd() == null ? "" : article.getContentMd();
            if (section.length() > MAX_FULL_TEXT_CHARS) {
                section = section.substring(0, MAX_FULL_TEXT_CHARS);
            }
        }
        String sectionTitle = wholeArticle || request.sectionTitle() == null
                ? null : request.sectionTitle().trim();

        QuizDtos.QuestionGenResponse generated = aiGateway.generateQuestions(userId, Map.of(
                "title", article.getTitle() == null ? "" : article.getTitle(),
                "articleType", article.getSourceType() == null ? "CUSTOM" : article.getSourceType().name(),
                "level", article.getCefrLevel() == null ? "unknown" : article.getCefrLevel(),
                "difficulty", difficulty,
                "count", String.valueOf(QUESTIONS_PER_SET),
                "sectionTitle", sectionTitle == null ? "(whole article)" : sectionTitle,
                "sectionContent", section));

        List<Question> cleaned = generated.questions().stream()
                .filter(Objects::nonNull)
                .map(raw -> toQuestion(raw, articleId, generated))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .limit(QUESTIONS_PER_SET)
                .toList();
        if (cleaned.size() < MIN_QUIZABLE_QUESTIONS) {
            throw ApiException.unavailable("AI 出题质量不达标，请重试");
        }

        ReadingQuizSet set = new ReadingQuizSet();
        set.setUserId(userId);
        set.setArticleId(articleId);
        set.setDifficulty(difficulty);
        set.setSectionTitle(sectionTitle);
        set.setTotalCount(cleaned.size());
        set.setPromptVersion(generated.promptVersion());
        set.setProvider(generated.provider());
        set.setModel(generated.model());
        setRepository.save(set);

        for (int i = 0; i < cleaned.size(); i++) {
            cleaned.get(i).setQuizSetId(set.getId());
            cleaned.get(i).setSortOrder(i + 1);
        }
        questionRepository.saveAll(cleaned);

        return new QuizDtos.QuizGenerateResponse(generated.provider(), generated.model(),
                generated.promptVersion(), toSetDto(set, cleaned, true));
    }

    /** Recent sets for this (user, article); the ungraded ones carry their questions. */
    @Transactional(readOnly = true)
    public QuizDtos.QuizListResponse list(Long userId, Long articleId) {
        requireArticle(articleId);
        List<QuizDtos.QuizSetDto> dtos = setRepository
                .findTop10ByUserIdAndArticleIdOrderByIdDesc(userId, articleId).stream()
                .map(set -> toSetDto(set, setQuestions(set.getId()), set.getSubmittedAt() == null))
                .toList();
        return new QuizDtos.QuizListResponse(dtos);
    }

    /**
     * Grades one set: server-side letter comparison, one {@link QuestionAttempt} row
     * per graded question, then the per-question result (correct answer + explanation)
     * is returned. Resubmitting a graded set replays the stored result unchanged.
     */
    @Transactional
    public QuizDtos.QuizSubmitResponse submit(Long userId, Long setId, QuizDtos.QuizSubmitRequest request) {
        ReadingQuizSet set = setRepository.findById(setId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("QUIZ_NOT_FOUND", "测验不存在"));
        List<Question> questions = setQuestions(setId);
        Map<Long, String> submitted = request.answers() == null ? Map.of() : request.answers().stream()
                .collect(Collectors.toMap(QuizDtos.QuizAnswerDto::questionId,
                        QuizDtos.QuizAnswerDto::answer, (left, right) -> right));

        List<QuizDtos.QuizResultItem> items = new ArrayList<>();
        int correct = 0;
        if (set.getSubmittedAt() == null) {
            int gradedCorrect = 0;
            for (Question question : questions) {
                List<String> options = readOptions(question.getOptionsJson());
                String letter = toLetter(submitted.get(question.getId()), options);
                boolean isRight = !letter.isEmpty() && letter.equalsIgnoreCase(question.getAnswer());
                gradedCorrect += isRight ? 1 : 0;
                QuestionAttempt attempt = new QuestionAttempt();
                attempt.setUserId(userId);
                attempt.setDomain(Question.DOMAIN_READING);
                attempt.setArticleId(set.getArticleId());
                attempt.setQuestionKey(question.getQuestionKey());
                attempt.setAnswer(letter);
                attempt.setCorrect(isRight);
                attemptRepository.save(attempt);
                items.add(toResultItem(question, options, letter, isRight));
            }
            set.setCorrectCount(gradedCorrect);
            set.setSubmittedAt(Instant.now());
            setRepository.save(set);
            correct = gradedCorrect;
        } else {
            Map<String, QuestionAttempt> stored = latestAttempts(userId, questions);
            for (Question question : questions) {
                List<String> options = readOptions(question.getOptionsJson());
                QuestionAttempt attempt = stored.get(question.getQuestionKey());
                boolean isRight = attempt != null && attempt.isCorrect();
                items.add(toResultItem(question, options, attempt == null ? "" : attempt.getAnswer(), isRight));
                correct += isRight ? 1 : 0;
            }
        }
        int total = questions.size();
        double score = total == 0 ? 100 : correct * 100.0 / total;
        return new QuizDtos.QuizSubmitResponse(total, correct, score,
                "AI 自测完成，答对 " + correct + "/" + total + " 题。", items);
    }

    // ---- helpers ------------------------------------------------------------

    private List<Question> setQuestions(Long setId) {
        return questionRepository.findByDomainAndQuizSetIdOrderBySortOrderAsc(
                Question.DOMAIN_READING, setId);
    }

    /** One attempt row per question for the replay path; the ledger is append-only. */
    private Map<String, QuestionAttempt> latestAttempts(Long userId, List<Question> questions) {
        List<String> keys = questions.stream().map(Question::getQuestionKey).toList();
        Map<String, QuestionAttempt> latest = new HashMap<>();
        if (keys.isEmpty()) {
            return latest;
        }
        for (QuestionAttempt attempt : attemptRepository
                .findByUserIdAndDomainAndQuestionKeyIn(userId, Question.DOMAIN_READING, keys)) {
            latest.merge(attempt.getQuestionKey(), attempt,
                    (left, right) -> right.getId() > left.getId() ? right : left);
        }
        return latest;
    }

    private ReadingArticle requireArticle(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
    }

    private String normalizeDifficulty(String difficulty) {
        String value = difficulty == null ? "" : difficulty.trim().toUpperCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(value)) {
            throw ApiException.badRequest("QUIZ_DIFFICULTY", "难度只支持 EASY / NORMAL / HARD");
        }
        return value;
    }

    /**
     * Validates one model question and freezes the answer to an option letter.
     * Rejects non-MCQ shapes; accepts an answer given as the option's own text
     * (models sometimes echo "C. ..." or the option body instead of the letter).
     */
    private Optional<Question> toQuestion(QuizDtos.GenQuestion raw, Long articleId,
                                          QuizDtos.QuestionGenResponse generated) {
        if (raw.type() != null && !"MCQ".equalsIgnoreCase(raw.type().trim())) {
            return Optional.empty();
        }
        if (raw.prompt() == null || raw.prompt().isBlank()) {
            return Optional.empty();
        }
        List<String> options = raw.options() == null ? List.of() : raw.options().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(option -> !option.isEmpty())
                .toList();
        if (options.size() != 4) {
            return Optional.empty();
        }
        String answer = toLetter(raw.answer(), options);
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        Question entity = new Question();
        entity.setQuestionKey("quiz-" + UUID.randomUUID());
        entity.setDomain(Question.DOMAIN_READING);
        entity.setArticleId(articleId);
        entity.setType("MCQ");
        entity.setSource("AI");
        entity.setPrompt(raw.prompt().trim());
        entity.setOptionsJson(writeOptions(options));
        entity.setAnswer(answer);
        entity.setExplanation(raw.explanation() == null ? "" : raw.explanation().trim());
        entity.setSkill(raw.skill() == null ? "" : raw.skill().trim());
        entity.setPromptVersion(generated.promptVersion());
        entity.setProvider(generated.provider());
        entity.setModel(generated.model());
        return Optional.of(entity);
    }

    /** Resolves a raw answer to "A"–"D": letter forms first, then option-text echo. */
    private static String toLetter(String raw, List<String> options) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.length() == 1 && upper.charAt(0) >= 'A' && upper.charAt(0) <= 'D') {
            return upper;
        }
        String stripped = upper.replaceAll("^[A-D][.、．)）]\\s*", "");
        if (stripped.length() == 1 && stripped.charAt(0) >= 'A' && stripped.charAt(0) <= 'D') {
            return stripped;
        }
        for (int i = 0; i < options.size(); i++) {
            String option = normalize(options.get(i));
            String candidate = normalize(value);
            if (option.equals(candidate)
                    || option.equals(normalize(stripped))
                    || (option.contains(candidate) && candidate.length() >= 8)) {
                return String.valueOf((char) ('A' + i));
            }
        }
        return "";
    }

    private QuizDtos.QuizSetDto toSetDto(ReadingQuizSet set, List<Question> questions,
                                         boolean includeQuestions) {
        List<QuizDtos.QuizQuestionDto> dtos = includeQuestions
                ? questions.stream()
                        .map(question -> new QuizDtos.QuizQuestionDto(question.getId(), question.getPrompt(),
                                readOptions(question.getOptionsJson())))
                        .toList()
                : List.of();
        return new QuizDtos.QuizSetDto(set.getId(), set.getDifficulty(), set.getSectionTitle(),
                set.getTotalCount(), set.getSubmittedAt() != null, set.getCorrectCount(),
                set.getCreatedAt(), set.getSubmittedAt(), dtos);
    }

    private QuizDtos.QuizResultItem toResultItem(Question question, List<String> options,
                                                 String yourAnswer, boolean isCorrect) {
        return new QuizDtos.QuizResultItem(question.getId(), question.getPrompt(), options,
                yourAnswer == null ? "" : yourAnswer, question.getAnswer(), isCorrect,
                question.getExplanation() == null ? "" : question.getExplanation());
    }

    private String writeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise quiz options", ex);
        }
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
