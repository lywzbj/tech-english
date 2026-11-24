package com.techenglish.grammar;

import com.techenglish.identity.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grammar")
@RequiredArgsConstructor
public class GrammarController {

    private final GrammarService grammarService;

    @GetMapping("/path")
    public GrammarDtos.GrammarPathResponse path(@AuthenticationPrincipal AuthUser user) {
        return grammarService.path(user.id());
    }

    @GetMapping("/topics/{slug}")
    public GrammarDtos.LessonResponse lesson(@AuthenticationPrincipal AuthUser user,
                                             @PathVariable String slug) {
        return grammarService.lesson(user.id(), slug);
    }

    @PostMapping("/topics/{slug}/attempts")
    public GrammarDtos.AttemptsResponse submit(@AuthenticationPrincipal AuthUser user,
                                               @PathVariable String slug,
                                               @Valid @RequestBody GrammarDtos.AttemptsRequest request) {
        return grammarService.submit(user.id(), slug, request);
    }
}
