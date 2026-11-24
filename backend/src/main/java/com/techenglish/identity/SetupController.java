package com.techenglish.identity;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private final AuthService authService;

    @GetMapping("/status")
    public IdentityDtos.SetupStatusResponse status() {
        return new IdentityDtos.SetupStatusResponse(authService.needsSetup());
    }

    @PostMapping
    public IdentityDtos.AuthResponse setup(@Valid @RequestBody IdentityDtos.SetupRequest request,
                                           HttpServletResponse response) {
        return authService.setup(request, response);
    }
}
