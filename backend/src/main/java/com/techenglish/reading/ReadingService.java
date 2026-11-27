package com.techenglish.reading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import com.techenglish.plan.LearningEventService;
import com.techenglish.question.Question;
import com.techenglish.question.QuestionAttempt;
import com.techenglish.question.QuestionAttemptRepository;
import com.techenglish.question.QuestionRepository;
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
    private final ReadingTagRepository tagRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAttemptRepository attemptRepository;
    private final ReadingSessionRepository sessionRepository;
    private final ReadingProgressRepository progressRepository;
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
        Map<Long, Integer> percentByArticleId = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(ReadingProgress::getArticleId,
                        ReadingProgress::getScrollPercent, Math::max));
        Map<Long, ReadingTag> tagById = tagRepository.findByDeletedOrderBySortOrderAscIdAsc(0).stream()
                .collect(Collectors.toMap(ReadingTag::getId, tag -> tag));
        List<ReadingDtos.ArticleSummary> summaries = articles.stream()
                .map(article -> toSummary(article, readArticleIds.contains(article.getId()),
                        percentByArticleId.getOrDefault(article.getId(), 0),
                        article.getTagId() == null ? null : tagById.get(article.getTagId())))
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
        int scrollPercent = progressRepository.findByUserIdAndArticleId(userId, articleId)
                .map(ReadingProgress::getScrollPercent)
                .orElse(0);
        return new ReadingDtos.ArticleDetail(
                toSummary(article, read, scrollPercent,
                        article.getTagId() == null ? null : tagRepository.findById(article.getTagId()).orElse(null)),
                article.getContentMd(),
                readingQuestions(articleId).stream()
                        .map(this::toQuestion)
                        .toList(),
                scrollPercent
        );
    }

    @Transactional
    public void saveProgress(Long userId, Long articleId, int scrollPercent) {
        articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
        upsertProgress(userId, articleId, scrollPercent);
    }

    private void upsertProgress(Long userId, Long articleId, int scrollPercent) {
        ReadingProgress progress = progressRepository.findByUserIdAndArticleId(userId, articleId)
                .orElseGet(() -> {
                    ReadingProgress created = new ReadingProgress();
                    created.setUserId(userId);
                    created.setArticleId(articleId);
                    return created;
                });
        progress.setScrollPercent(scrollPercent);
        progressRepository.save(progress);
    }

    @Transactional
    public ReadingDtos.ArticleDetail importText(Long userId, ReadingDtos.ImportArticleRequest request) {
        Long tagId = request.tagId();
        ReadingSourceType type = ReadingSourceType.CUSTOM;
        if (tagId != null) {
            ReadingTag tag = liveTag(tagId);
            if (tag.getEnableStatus() != 0) {
                throw ApiException.badRequest("TAG_DISABLED", "该文档类型已停用，请选择其他类型");
            }
            if (tag.getSourceType() != null) {
                type = tag.getSourceType();
            }
        } else if (request.sourceType() != null && !request.sourceType().isBlank()) {
            try {
                type = ReadingSourceType.valueOf(request.sourceType().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("INVALID_SOURCE_TYPE", "文章类型不正确");
            }
            tagId = tagRepository.findBySourceTypeAndDeleted(type, 0)
                    .map(ReadingTag::getId)
                    .orElse(null);
        }
        String content = MarkdownNormalizer.normalize(request.contentMd().trim());
        if (content.length() < 80) {
            throw ApiException.badRequest("CONTENT_TOO_SHORT", "文章内容太短，至少粘贴一段完整文本");
        }
        ReadingArticle article = new ReadingArticle();
        article.setTitle(request.title().trim());
        article.setSubtitle(request.subtitle());
        article.setSourceType(type);
        article.setTagId(tagId);
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
                title, "Imported from the web", imported.markdown(), imported.url(), null, request.tagId()));
    }

    @Transactional
    public ReadingDtos.CompleteResponse complete(Long userId, Long articleId, ReadingDtos.CompleteRequest request) {
        ReadingArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
        List<Question> questions = readingQuestions(articleId);
        Map<Long, String> submitted = request.answers() == null ? Map.of() : request.answers().stream()
                .collect(Collectors.toMap(ReadingDtos.AnswerDto::questionId,
                        ReadingDtos.AnswerDto::answer, (left, right) -> right));
        int correct = 0;
        for (Question question : questions) {
            String answer = submitted.get(question.getId());
            if (answer == null) {
                continue; // 未作答不落流水：attempt 只记录真实提交过的答案
            }
            boolean isCorrect = normalize(answer).equals(normalize(question.getAnswer()));
            if (isCorrect) {
                correct++;
            }
            QuestionAttempt attempt = new QuestionAttempt();
            attempt.setUserId(userId);
            attempt.setDomain(Question.DOMAIN_READING);
            attempt.setArticleId(articleId);
            attempt.setQuestionKey(question.getQuestionKey());
            attempt.setAnswer(answer);
            attempt.setCorrect(isCorrect);
            attemptRepository.save(attempt);
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
        upsertProgress(userId, articleId, 0);
        learningEventService.record(userId, "ARTICLE_COMPLETED", "ARTICLE", article.getId(),
                Map.of("minutes", request.minutes(), "correct", correct, "total", questions.size()));
        double score = questions.isEmpty() ? 100 : correct * 100.0 / questions.size();
        String message = questions.isEmpty()
                ? "阅读已记录。可以选一段话让 AI 帮你拆解。"
                : "完成阅读，答对 " + correct + "/" + questions.size() + " 题。";
        return new ReadingDtos.CompleteResponse(questions.size(), correct, score, message);
    }

    private ReadingDtos.ArticleSummary toSummary(ReadingArticle article, boolean read, int scrollPercent,
                                                 ReadingTag tag) {
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
                read,
                // Keep this as the raw resume point: the list shows completion from
                // `read` separately, so mixing 100 in here made "scrolled to the end"
                // look identical to "finished the article".
                scrollPercent,
                tag == null ? null : tag.getId(),
                tag != null ? tag.getName() : fallbackTagName(article.getSourceType())
        );
    }

    @Transactional(readOnly = true)
    public List<ReadingDtos.TagDto> listTags() {
        return tagRepository.findByDeletedOrderBySortOrderAscIdAsc(0).stream()
                .map(this::toTagDto)
                .toList();
    }

    @Transactional
    public ReadingDtos.TagDto createTag(ReadingDtos.TagRequest request) {
        String name = request.name().trim();
        if (tagRepository.findByNameAndDeleted(name, 0).isPresent()) {
            throw ApiException.badRequest("TAG_NAME_EXISTS", "标签名称已存在");
        }
        ReadingTag tag = new ReadingTag();
        tag.setName(name);
        tag.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextTagSortOrder());
        tag = tagRepository.save(tag);
        return toTagDto(tag);
    }

    @Transactional
    public ReadingDtos.TagDto updateTag(Long id, ReadingDtos.TagRequest request) {
        ReadingTag tag = liveTag(id);
        String name = request.name().trim();
        tagRepository.findByNameAndDeleted(name, 0)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.badRequest("TAG_NAME_EXISTS", "标签名称已存在");
                });
        tag.setName(name);
        if (request.sortOrder() != null) {
            tag.setSortOrder(request.sortOrder());
        }
        tag = tagRepository.save(tag);
        return toTagDto(tag);
    }

    @Transactional
    public ReadingDtos.TagDto setTagStatus(Long id, boolean enabled) {
        ReadingTag tag = liveTag(id);
        tag.setEnableStatus(enabled ? 0 : 1);
        tag = tagRepository.save(tag);
        return toTagDto(tag);
    }

    private ReadingTag liveTag(Long id) {
        return tagRepository.findById(id)
                .filter(tag -> tag.getDeleted() == 0)
                .orElseThrow(() -> ApiException.notFound("TAG_NOT_FOUND", "标签不存在"));
    }

    private int nextTagSortOrder() {
        return tagRepository.findByDeletedOrderBySortOrderAscIdAsc(0).stream()
                .mapToInt(ReadingTag::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    private ReadingDtos.TagDto toTagDto(ReadingTag tag) {
        return new ReadingDtos.TagDto(
                tag.getId(),
                tag.getName(),
                tag.getSortOrder(),
                tag.getEnableStatus() == 0,
                articleRepository.countByTagId(tag.getId())
        );
    }

    /** Display name for articles without a managed tag (seeded content, pre-V5 rows). */
    private String fallbackTagName(ReadingSourceType sourceType) {
        return switch (sourceType) {
            case IELTS -> "雅思学术";
            case TECH -> "技术文档";
            case CUSTOM -> "我的导入";
        };
    }

    /** Static reading questions of one article, in display order (统一题库中阅读域的行). */
    private List<Question> readingQuestions(Long articleId) {
        return questionRepository.findByDomainAndArticleIdAndDeletedOrderBySortOrderAsc(
                Question.DOMAIN_READING, articleId, 0);
    }

    private ReadingDtos.QuestionDto toQuestion(Question question) {
        return new ReadingDtos.QuestionDto(
                question.getId(),
                question.getType(),
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
