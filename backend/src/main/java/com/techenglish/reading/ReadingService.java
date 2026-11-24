package com.techenglish.reading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import com.techenglish.plan.LearningEventService;
import com.techenglish.vector.ArticleImportedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadingService {

    private final ReadingArticleRepository articleRepository;
    private final ReadingQuestionRepository questionRepository;
    private final ReadingSessionRepository sessionRepository;
    private final UrlArticleImporter urlArticleImporter;
    private final LearningEventService learningEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ReadingDtos.ArticleListResponse list(Long userId, String type) {
        List<ReadingArticle> articles = articleRepository.findAllByOrderBySourceTypeAscIdAsc();
        if (type != null && !type.isBlank()) {
            ReadingSourceType sourceType;
            try {
                sourceType = ReadingSourceType.valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("INVALID_SOURCE_TYPE", "文章类型不正确");
            }
            articles = articles.stream().filter(article -> article.getSourceType() == sourceType).toList();
        }
        List<ReadingSession> sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId);
        Set<Long> readArticleIds = sessions.stream()
                .map(ReadingSession::getArticleId)
                .collect(Collectors.toSet());
        List<ReadingDtos.ArticleSummary> summaries = articles.stream()
                .map(article -> toSummary(article, readArticleIds.contains(article.getId())))
                .toList();
        long readCount = summaries.stream().filter(ReadingDtos.ArticleSummary::read).count();
        return new ReadingDtos.ArticleListResponse(
                summaries.size(),
                readCount,
                summaries
        );
    }

    @Transactional(readOnly = true)
    public ReadingDtos.ArticleDetail detail(Long userId, Long articleId) {
        ReadingArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
        boolean read = sessionRepository.existsByUserIdAndArticleId(userId, articleId);
        return new ReadingDtos.ArticleDetail(
                toSummary(article, read),
                article.getContentMd(),
                questionRepository.findByArticleIdOrderBySortOrderAsc(articleId).stream()
                        .map(this::toQuestion)
                        .toList()
        );
    }

    @Transactional
    public ReadingDtos.ArticleDetail importText(Long userId, ReadingDtos.ImportArticleRequest request) {
        ReadingSourceType type = ReadingSourceType.CUSTOM;
        if (request.sourceType() != null && !request.sourceType().isBlank()) {
            try {
                type = ReadingSourceType.valueOf(request.sourceType().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("INVALID_SOURCE_TYPE", "文章类型不正确");
            }
        }
        String content = request.contentMd().trim();
        if (content.length() < 80) {
            throw ApiException.badRequest("CONTENT_TOO_SHORT", "文章内容太短，至少粘贴一段完整文本");
        }
        ReadingArticle article = new ReadingArticle();
        article.setTitle(request.title().trim());
        article.setSubtitle(request.subtitle());
        article.setSourceType(type);
        article.setSourceUrl(request.sourceUrl());
        article.setSummary(buildSummary(content));
        article.setContentMd(content);
        int words = countWords(content);
        article.setWordCount(words);
        article.setCefrLevel("CUSTOM");
        article.setTopicsJson("[]");
        article.setEstimatedMinutes(Math.max(1, (int) Math.ceil(words / 180.0)));
        article = articleRepository.save(article);
        learningEventService.record(userId, "ARTICLE_IMPORTED", "ARTICLE", article.getId(),
                Map.of("type", type.name(), "wordCount", words));
        eventPublisher.publishEvent(new ArticleImportedEvent(article.getId()));
        return detail(userId, article.getId());
    }

    @Transactional
    public ReadingDtos.ArticleDetail importUrl(Long userId, ReadingDtos.ImportUrlRequest request) {
        UrlArticleImporter.ImportedArticle imported = urlArticleImporter.fetch(request.url());
        String title = request.title();
        if (title == null || title.isBlank()) {
            title = imported.title();
        }
        if (title == null || title.isBlank()) {
            title = "Imported article";
        }
        return importText(userId, new ReadingDtos.ImportArticleRequest(
                title, "Imported from the web", imported.markdown(), imported.url(), "CUSTOM"));
    }

    @Transactional
    public ReadingDtos.CompleteResponse complete(Long userId, Long articleId, ReadingDtos.CompleteRequest request) {
        ReadingArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
        List<ReadingQuestion> questions = questionRepository.findByArticleIdOrderBySortOrderAsc(articleId);
        Map<Long, String> submitted = request.answers() == null ? Map.of() : request.answers().stream()
                .collect(Collectors.toMap(ReadingDtos.AnswerDto::questionId,
                        ReadingDtos.AnswerDto::answer, (left, right) -> right));
        int correct = 0;
        for (ReadingQuestion question : questions) {
            String answer = submitted.get(question.getId());
            if (answer != null && normalize(answer).equals(normalize(question.getAnswer()))) {
                correct++;
            }
        }
        ReadingSession session = new ReadingSession();
        session.setUserId(userId);
        session.setArticleId(articleId);
        session.setStartedAt(Instant.now().minusSeconds(request.minutes() * 60L));
        session.setFinishedAt(Instant.now());
        session.setMinutes(request.minutes());
        session.setQuestionsCorrect(correct);
        session.setQuestionsTotal(questions.size());
        sessionRepository.save(session);
        learningEventService.record(userId, "ARTICLE_COMPLETED", "ARTICLE", article.getId(),
                Map.of("minutes", request.minutes(), "correct", correct, "total", questions.size()));
        double score = questions.isEmpty() ? 100 : correct * 100.0 / questions.size();
        String message = questions.isEmpty()
                ? "阅读已记录。可以选一段话让 AI 帮你拆解。"
                : "完成阅读，答对 " + correct + "/" + questions.size() + " 题。";
        return new ReadingDtos.CompleteResponse(questions.size(), correct, score, message);
    }

    private ReadingDtos.ArticleSummary toSummary(ReadingArticle article, boolean read) {
        return new ReadingDtos.ArticleSummary(
                article.getId(),
                article.getTitle(),
                article.getSubtitle(),
                article.getSourceType().name(),
                article.getSummary(),
                article.getWordCount(),
                article.getCefrLevel(),
                article.getEstimatedMinutes(),
                readList(article.getTopicsJson()),
                article.getSourceUrl(),
                read
        );
    }

    private ReadingDtos.QuestionDto toQuestion(ReadingQuestion question) {
        return new ReadingDtos.QuestionDto(
                question.getId(),
                question.getType().name(),
                question.getPrompt(),
                readList(question.getOptionsJson())
        );
    }

    private List<String> readList(String json) {
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int countWords(String content) {
        String prose = content.replaceAll("```[\\s\\S]*?```", " ");
        String[] words = prose.replaceAll("[#>*_`\\-\\[\\]()]", " ")
                .trim()
                .split("\\s+");
        int count = 0;
        for (String word : words) {
            if (word.matches(".*[A-Za-z].*")) {
                count++;
            }
        }
        return count;
    }

    private String buildSummary(String content) {
        String plain = content.replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("[#>*_`\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= 220 ? plain : plain.substring(0, 220) + "…";
    }
}
