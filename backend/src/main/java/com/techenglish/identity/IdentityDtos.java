package com.techenglish.identity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record SetupStatusResponse(boolean needsSetup) {
    }

    public record SetupRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 80) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record UserDto(Long id, String email, String displayName) {
    }

    public record ProfileDto(
            BigDecimal targetBand,
            LocalDate examDate,
            int dailyMinutes,
            String cefrLevel,
            List<String> techStack,
            List<String> interests,
            String explanationLanguage,
            boolean onboardingCompleted
    ) {
    }

    public record AuthResponse(
            String accessToken,
            long expiresInSeconds,
            UserDto user,
            ProfileDto profile
    ) {
    }

    public record MeResponse(UserDto user, ProfileDto profile) {
    }

    public record ProfileUpdateRequest(
            @DecimalMin("4.0") @DecimalMax("9.0") BigDecimal targetBand,
            @Future LocalDate examDate,
            @Min(15) @Max(240) Integer dailyMinutes,
            @Size(max = 10) String cefrLevel,
            @Size(max = 20) List<@Size(max = 40) String> techStack,
            @Size(max = 20) List<@Size(max = 40) String> interests,
            @Size(max = 20) String explanationLanguage,
            Boolean onboardingCompleted
    ) {
    }
}
