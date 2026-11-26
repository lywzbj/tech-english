package com.techenglish.reading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.ai.AiGateway;
import com.techenglish.ai.PreviewDtos;
import com.techenglish.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Article preview sheet: AI extracts a draft (never stored on its own), the
 * learner ticks the items they want, and only the confirmed subset is saved —
 * keyed by (user, article) so each learner gets a personalised sheet.
 */
@Service
@RequiredArgsConstructor
public class ReadingPreviewService {

    /** Cap of article text sent to the model; long documents get cut, preview need not see everything. */
    private static final int MAX_PROMPT_CHARS = 60000;

    private final ReadingArticleRepository articleRepository;
    private final ReadingPreviewRepository previewRepository;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PreviewDtos.PreviewStateResponse state(Long userId, Long articleId) {
        requireArticle(articleId);
        return previewRepository.findByUserIdAndArticleId(userId, articleId)
                .map(preview -> new PreviewDtos.PreviewStateResponse(true, toContent(preview)))
                .orElseGet(() -> new PreviewDtos.PreviewStateResponse(false, null));
    }

    /** Extract a draft via AI and return it as-is; nothing is persisted until save(). */
    public PreviewDtos.PreviewGenerateResponse generate(Long userId, Long articleId) {
        ReadingArticle article = requireArticle(articleId);
        String content = article.getContentMd() == null ? "" : article.getContentMd();
        if (content.length() > MAX_PROMPT_CHARS) {
            content = content.substring(0, MAX_PROMPT_CHARS);
        }
        return aiGateway.generatePreview(userId, Map.of(
                "title", article.getTitle() == null ? "" : article.getTitle(),
                "articleType", article.getSourceType() == null ? "CUSTOM" : article.getSourceType().name(),
                "level", article.getCefrLevel() == null ? "unknown" : article.getCefrLevel(),
                "content", content));
    }

    @Transactional
    public PreviewDtos.PreviewContent save(Long userId, Long articleId, PreviewDtos.PreviewSaveRequest request) {
        requireArticle(articleId);
        ReadingPreview preview = previewRepository.findByUserIdAndArticleId(userId, articleId)
                .orElseGet(() -> {
                    ReadingPreview created = new ReadingPreview();
                    created.setUserId(userId);
                    created.setArticleId(articleId);
                    return created;
                });
        preview.setWordsJson(writeJson(request.words()));
        preview.setGrammarJson(writeJson(request.grammar()));
        preview.setPromptVersion(request.promptVersion());
        preview.setProvider(request.provider());
        preview.setModel(request.model());
        previewRepository.save(preview);
        return new PreviewDtos.PreviewContent(
                request.words() == null ? List.of() : request.words(),
                request.grammar() == null ? List.of() : request.grammar(),
                preview.getPromptVersion(),
                preview.getUpdatedAt());
    }

    private ReadingArticle requireArticle(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
    }

    private PreviewDtos.PreviewContent toContent(ReadingPreview preview) {
        return new PreviewDtos.PreviewContent(
                readWords(preview.getWordsJson()),
                readGrammar(preview.getGrammarJson()),
                preview.getPromptVersion(),
                preview.getUpdatedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise preview sheet", ex);
        }
    }

    private List<PreviewDtos.PreviewWord> readWords(String json) {
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

    private List<PreviewDtos.PreviewGrammar> readGrammar(String json) {
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
}
