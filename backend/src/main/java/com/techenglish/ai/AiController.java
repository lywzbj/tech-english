package com.techenglish.ai;

import com.techenglish.identity.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiGateway aiGateway;

    @GetMapping("/status")
    public AiDtos.AiStatusResponse status() {
        return aiGateway.status();
    }

    @PostMapping("/explain")
    public AiDtos.ExplainResponse explain(@AuthenticationPrincipal AuthUser user,
                                          @Valid @RequestBody AiDtos.ExplainRequest request) {
        return aiGateway.explain(user.id(), request);
    }

    /** Post-reading settlement: judge one guessed word against the article sentence. */
    @PostMapping("/judge-guess")
    public AiDtos.JudgeGuessResponse judgeGuess(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody AiDtos.JudgeGuessRequest request) {
        return aiGateway.judgeGuess(user.id(), request);
    }
}
