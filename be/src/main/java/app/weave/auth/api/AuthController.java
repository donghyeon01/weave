package app.weave.auth.api;

import app.weave.auth.domain.User;
import app.weave.auth.service.AuthService;
import app.weave.auth.service.SessionService;
import app.weave.auth.service.TokenHasher;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final Duration REFRESH_MAX_AGE = Duration.ofDays(14);

    private final AuthService authService;
    private final SessionService sessionService;
    // cross-site 배포(fe와 API가 다른 도메인)는 None 필수 — dev/prod 분기는 env로 주입한다
    private final String cookieSameSite;

    public AuthController(AuthService authService, SessionService sessionService,
            @Value("${weave.cookie-samesite:Strict}") String cookieSameSite) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookieSameSite = normalizeSameSite(cookieSameSite);
    }

    // 대소문자 오입력("none" 등)은 브라우저가 SameSite를 무시해 Lax 폴백 — Strict/Lax/None으로 정규화한다
    private static String normalizeSameSite(String value) {
        if (value == null) {
            return "Strict";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "none" -> "None";
            case "lax" -> "Lax";
            default -> "Strict";
        };
    }

    @PostMapping("/oauth/kakao")
    public ResponseEntity<TokenResponse> kakao(@Valid @RequestBody KakaoRequest req,
            HttpServletRequest http) {
        var result = authService.loginWithKakao(req.code(), req.redirectUri(),
                http.getHeader("User-Agent"), http.getRemoteAddr());
        return tokenResponse(result, HttpStatus.OK);
    }

    @PostMapping("/demo")
    public ResponseEntity<TokenResponse> demo(HttpServletRequest http) {
        var result = authService.loginDemo(http.getHeader("User-Agent"), http.getRemoteAddr());
        return tokenResponse(result, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        var rotated = sessionService.rotateAndValidate(
                TokenHasher.sha256(refreshToken), http.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotated.refreshToken()).toString())
                .body(new RefreshResponse(rotated.accessToken(), "Bearer", 1800));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessionService.revoke(TokenHasher.sha256(refreshToken));
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expireCookie().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(AuthService.LoginResult result,
            HttpStatus status) {
        User u = result.user();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(new TokenResponse(result.accessToken(), "Bearer", 1800,
                        new UserInfo(u.getId(), u.getEmail(), u.getName(), u.getNickname(),
                                u.getProfileImage(), u.getProvider().name().toLowerCase())));
    }

    private ResponseCookie refreshCookie(String raw) {
        return ResponseCookie.from(REFRESH_COOKIE, raw)
                .httpOnly(true)
                .secure(true)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(REFRESH_MAX_AGE)
                .build();
    }

    private ResponseCookie expireCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(true).sameSite(cookieSameSite)
                .path("/api/auth").maxAge(0)
                .build();
    }

    public record KakaoRequest(@NotBlank String code, @NotBlank String redirectUri) {
    }

    public record TokenResponse(String accessToken, String tokenType, int expiresIn,
            UserInfo user) {
    }

    public record RefreshResponse(String accessToken, String tokenType, int expiresIn) {
    }

    public record UserInfo(Long id, String email, String name, String nickname,
            String profileImage, String provider) {
    }
}
