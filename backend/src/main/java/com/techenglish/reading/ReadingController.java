package com.techenglish.reading;

import com.techenglish.ai.AiDtos;
import com.techenglish.ai.PreviewDtos;
import com.techenglish.ai.QuizDtos;
import com.techenglish.identity.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;
    private final ReadingPreviewService previewService;
    private final ReadingVocabService vocabService;
    private final ReadingQuizService quizService;

    @GetMapping("/articles")
    public ReadingDtos.ArticleListResponse list(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(required = false) String type) {
        return readingService.list(user.id(), type);
    }

    @GetMapping("/tags")
    public List<ReadingDtos.TagDto> listTags() {
        return readingService.listTags();
    }

    @PostMapping("/tags")
    public ReadingDtos.TagDto createTag(@Valid @RequestBody ReadingDtos.TagRequest request) {
        return readingService.createTag(request);
    }

    @PutMapping("/tags/{id}")
    public ReadingDtos.TagDto updateTag(@PathVariable Long id,
                                        @Valid @RequestBody ReadingDtos.TagRequest request) {
        return readingService.updateTag(id, request);
    }

    @PutMapping("/tags/{id}/status")
    public ReadingDtos.TagDto setTagStatus(@PathVariable Long id,
                                           @Valid @RequestBody ReadingDtos.TagStatusRequest request) {
        return readingService.setTagStatus(id, request.enabled());
    }

    @GetMapping("/articles/{id}")
    public ReadingDtos.ArticleDetail detail(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable Long id) {
        return readingService.detail(user.id(), id);
    }

    @GetMapping("/articles/{id}/preview")
    public PreviewDtos.PreviewStateResponse previewState(@AuthenticationPrincipal AuthUser user,
                                                         @PathVariable Long id) {
        return previewService.state(user.id(), id);
    }

    @PostMapping("/articles/{id}/preview/generate")
    public PreviewDtos.PreviewGenerateResponse generatePreview(@AuthenticationPrincipal AuthUser user,
                                                               @PathVariable Long id) {
        return previewService.generate(user.id(), id);
    }

    @PutMapping("/articles/{id}/preview")
    public PreviewDtos.PreviewContent savePreview(@AuthenticationPrincipal AuthUser user,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody PreviewDtos.PreviewSaveRequest request) {
        return previewService.save(user.id(), id, request);
    }

    @PostMapping("/import")
    public ReadingDtos.ArticleDetail importText(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody ReadingDtos.ImportArticleRequest request) {
        return readingService.importText(user.id(), request);
    }

    @PostMapping("/import-url")
    public ReadingDtos.ArticleDetail importUrl(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody ReadingDtos.ImportUrlRequest request) {
        return readingService.importUrl(user.id(), request);
    }

    @PostMapping("/articles/{id}/progress")
    public void saveProgress(@AuthenticationPrincipal AuthUser user,
                             @PathVariable Long id,
                             @Valid @RequestBody ReadingDtos.ProgressRequest request) {
        readingService.saveProgress(user.id(), id, request.scrollPercent());
    }

    @PostMapping("/articles/{id}/complete")
    public ReadingDtos.CompleteResponse complete(@AuthenticationPrincipal AuthUser user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody ReadingDtos.CompleteRequest request) {
        return readingService.complete(user.id(), id, request);
    }

    /** Post-reading settlement: scan the article for missed high-value words. */
    @PostMapping("/articles/{id}/vocab-scan")
    public AiDtos.VocabScanResponse vocabScan(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long id,
                                              @Valid @RequestBody ReadingDtos.VocabScanRequest request) {
        return vocabService.scan(user.id(), id, request.knownWords());
    }

    /** AI 自测：按所选难度针对当前小节生成一套单选题并留档（答案只存服务端）。 */
    @PostMapping("/articles/{id}/quiz/generate")
    public QuizDtos.QuizGenerateResponse generateQuiz(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody QuizDtos.QuizGenerateRequest request) {
        return quizService.generate(user.id(), id, request);
    }

    /** 本文最近的自测套：未判分的返回题目用于恢复作答，已判分的只回成绩摘要。 */
    @GetMapping("/articles/{id}/quiz")
    public QuizDtos.QuizListResponse listQuiz(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long id) {
        return quizService.list(user.id(), id);
    }

    /** 提交答案 → 后端判分落库 → 返回逐题对错与解析。 */
    @PostMapping("/quiz/sets/{setId}/submit")
    public QuizDtos.QuizSubmitResponse submitQuiz(@AuthenticationPrincipal AuthUser user,
                                                  @PathVariable Long setId,
                                                  @Valid @RequestBody QuizDtos.QuizSubmitRequest request) {
        return quizService.submit(user.id(), setId, request);
    }
}
