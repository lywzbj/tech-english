package com.techenglish.reading;

import com.techenglish.ai.AiDtos;
import com.techenglish.ai.AiGateway;
import com.techenglish.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Post-reading vocabulary settlement: asks the AI gateway to scan one stored
 * article for high-value words the learner never marked or looked up. The
 * article text is loaded server-side (like the preview sheet) and the learner's
 * processed words are excluded twice: in the prompt and again defensively here.
 */
@Service
@RequiredArgsConstructor
public class ReadingVocabService {

    /** Cap of article text sent to the model; long documents get cut, scanning need not see everything. */
    private static final int MAX_PROMPT_CHARS = 60000;
    private static final int MAX_SCANNED_WORDS = 8;

    private final ReadingArticleRepository articleRepository;
    private final AiGateway aiGateway;

    public AiDtos.VocabScanResponse scan(Long userId, Long articleId, List<String> knownWords) {
        ReadingArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "文章不存在"));
        String content = article.getContentMd() == null ? "" : article.getContentMd();
        if (content.length() > MAX_PROMPT_CHARS) {
            content = content.substring(0, MAX_PROMPT_CHARS);
        }
        List<String> known = (knownWords == null ? List.<String>of() : knownWords).stream()
                .filter(Objects::nonNull)
                .map(word -> word.trim().toLowerCase(Locale.ROOT))
                .filter(word -> !word.isEmpty())
                .distinct()
                .toList();
        AiDtos.VocabScanResponse response = aiGateway.scanVocabulary(userId, Map.of(
                "title", article.getTitle() == null ? "" : article.getTitle(),
                "count", String.valueOf(MAX_SCANNED_WORDS),
                "processed", known.isEmpty() ? "(none)" : String.join(", ", known),
                "content", content));
        List<AiDtos.MissedWord> words = (response.words() == null ? List.<AiDtos.MissedWord>of() : response.words())
                .stream()
                .filter(Objects::nonNull)
                .filter(word -> word.word() != null && !word.word().isBlank())
                .filter(word -> !isKnown(word.word(), known))
                .limit(MAX_SCANNED_WORDS)
                .toList();
        return new AiDtos.VocabScanResponse(response.provider(), response.model(), words);
    }

    /** Treats inflected forms of a processed word as processed too, in either direction. */
    private static boolean isKnown(String word, List<String> known) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        return known.stream().anyMatch(k -> k.equals(w) || k.startsWith(w) || w.startsWith(k));
    }
}
