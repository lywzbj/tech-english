package com.techenglish.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techenglish.common.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String REFRESH_COOKIE = "refresh_token";

    private final AppUserRepository userRepository;
    private final LearnerProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${app.jwt.refresh-ttl-days:30}")
    private long refreshTtlDays;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    private final SecureRandom secureRandom = new SecureRandom();

    public boolean needsSetup() {
        return userRepository.count() == 0;
    }

    @Transactional
    public IdentityDtos.AuthResponse setup(IdentityDtos.SetupRequest request, HttpServletResponse response) {
        if (!needsSetup()) {
            throw ApiException.conflict("SETUP_COMPLETED", "账号已经初始化，请直接登录");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? email.substring(0, email.indexOf('@'))
                : request.displayName().trim();
        user.setDisplayName(displayName);
        user = userRepository.save(user);
        profileRepository.save(newProfile(user));
        return issueAuth(user, response);
    }

    @Transactional
    public IdentityDtos.AuthResponse login(IdentityDtos.LoginRequest request, HttpServletResponse response) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("邮箱或密码不正确"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("邮箱或密码不正确");
        }
        profileRepository.findByUserId(user.getId()).orElseGet(() -> profileRepository.save(newProfile(user)));
        return issueAuth(user, response);
    }

    @Transactional
    public IdentityDtos.AuthResponse refresh(String rawToken, HttpServletResponse response) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("登录已过期，请重新登录"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            clearRefreshCookie(response);
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        AppUser user = token.getUser();
        profileRepository.findByUserId(user.getId()).orElseGet(() -> profileRepository.save(newProfile(user)));
        return issueAuth(user, response);
    }

    @Transactional
    public void logout(String rawToken, HttpServletResponse response) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository.findByTokenHashAndRevokedFalse(hash(rawToken)).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        clearRefreshCookie(response);
    }

    @Transactional(readOnly = true)
    public IdentityDtos.MeResponse me(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在"));
        LearnerProfile profile = profileRepository.findByUserId(userId).orElse(null);
        return new IdentityDtos.MeResponse(toUser(user), toProfile(profile));
    }

    @Transactional
    public IdentityDtos.MeResponse updateProfile(Long userId, IdentityDtos.ProfileUpdateRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在"));
        LearnerProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> newProfile(user));

        if (request.targetBand() != null) {
            profile.setTargetBand(request.targetBand());
        }
        if (request.examDate() != null) {
            profile.setExamDate(request.examDate());
        }
        if (request.dailyMinutes() != null) {
            profile.setDailyMinutes(request.dailyMinutes());
        }
        if (request.cefrLevel() != null && !request.cefrLevel().isBlank()) {
            profile.setCefrLevel(request.cefrLevel().trim().toUpperCase(Locale.ROOT));
        }
        if (request.techStack() != null) {
            profile.setTechStack(writeList(request.techStack()));
        }
        if (request.interests() != null) {
            profile.setInterests(writeList(request.interests()));
        }
        if (request.explanationLanguage() != null && !request.explanationLanguage().isBlank()) {
            profile.setExplanationLanguage(request.explanationLanguage().trim());
        }
        if (Boolean.TRUE.equals(request.onboardingCompleted())) {
            if (profile.getTargetBand() == null || profile.getExamDate() == null) {
                throw ApiException.badRequest("PROFILE_INCOMPLETE", "请先填写目标分数和考试日期");
            }
            profile.setOnboardingCompleted(true);
        } else if (Boolean.FALSE.equals(request.onboardingCompleted())) {
            profile.setOnboardingCompleted(false);
        }
        profileRepository.save(profile);
        return new IdentityDtos.MeResponse(toUser(user), toProfile(profile));
    }

    private IdentityDtos.AuthResponse issueAuth(AppUser user, HttpServletResponse response) {
        String accessToken = jwtService.createAccessToken(user);
        String rawRefresh = newRefreshToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(hash(rawRefresh));
        refresh.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshTtlDays)));
        refreshTokenRepository.save(refresh);
        attachRefreshCookie(response, rawRefresh);
        LearnerProfile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        return new IdentityDtos.AuthResponse(
                accessToken,
                jwtService.accessTtlSeconds(),
                toUser(user),
                toProfile(profile)
        );
    }

    private LearnerProfile newProfile(AppUser user) {
        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setTechStack("[]");
        profile.setInterests("[]");
        return profile;
    }

    private IdentityDtos.UserDto toUser(AppUser user) {
        return new IdentityDtos.UserDto(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private IdentityDtos.ProfileDto toProfile(LearnerProfile profile) {
        if (profile == null) {
            return null;
        }
        return new IdentityDtos.ProfileDto(
                profile.getTargetBand(),
                profile.getExamDate(),
                profile.getDailyMinutes(),
                profile.getCefrLevel(),
                readList(profile.getTechStack()),
                readList(profile.getInterests()),
                profile.getExplanationLanguage(),
                profile.isOnboardingCompleted()
        );
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize profile list", ex);
        }
    }

    private List<String> readList(String json) {
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

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void attachRefreshCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(refreshTtlDays))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
