package com.techenglish.identity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;

    @GetMapping
    public IdentityDtos.MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    @PutMapping("/profile")
    public IdentityDtos.MeResponse updateProfile(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody IdentityDtos.ProfileUpdateRequest request) {
        return authService.updateProfile(user.id(), request);
    }
}
