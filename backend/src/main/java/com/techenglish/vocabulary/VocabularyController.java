package com.techenglish.vocabulary;

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
@RequestMapping("/api/v1/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping("/due")
    public VocabularyDtos.DueResponse due(@AuthenticationPrincipal AuthUser user,
                                          @RequestParam(defaultValue = "20") int limit) {
        return vocabularyService.due(user.id(), limit);
    }

    @GetMapping
    public VocabularyDtos.VocabularyListResponse list(@AuthenticationPrincipal AuthUser user) {
        return vocabularyService.list(user.id());
    }

    @PostMapping
    public VocabularyDtos.VocabCard add(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody VocabularyDtos.AddVocabRequest request) {
        return vocabularyService.add(user.id(), request);
    }

    @PostMapping("/{userVocabId}/review")
    public VocabularyDtos.ReviewResponse review(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable Long userVocabId,
                                                @Valid @RequestBody VocabularyDtos.ReviewRequest request) {
        return vocabularyService.review(user.id(), userVocabId, request.rating());
    }

    @GetMapping("/lookup")
    public VocabularyDtos.LookupResponse lookup(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam String word) {
        return vocabularyService.lookup(user.id(), word);
    }
}
