package com.techenglish.reading;

import com.techenglish.identity.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @GetMapping("/articles")
    public ReadingDtos.ArticleListResponse list(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(required = false) String type) {
        return readingService.list(user.id(), type);
    }

    @GetMapping("/articles/{id}")
    public ReadingDtos.ArticleDetail detail(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable Long id) {
        return readingService.detail(user.id(), id);
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
}
