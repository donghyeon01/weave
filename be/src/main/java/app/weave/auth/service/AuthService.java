package app.weave.auth.service;

import app.weave.auth.domain.RefreshToken;
import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.auth.jwt.JwtProvider;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEMO_EMAIL = "demo@weave.local";
    private static final int DEMO_LIMIT_PER_HOUR = 10;
    private static final List<String> DEFAULT_ROLES = List.of("ROLE_USER");

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final JwtProvider jwtProvider;
    private final KakaoOAuthClient kakaoClient;
    private final boolean demoEnabled;

    /** 데모 로그인 IP당 10회/시간 — 공개 토큰 발급 엔드포인트 남용 차단. */
    private final Cache<String, Integer> demoAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10_000)
            .build();

    public AuthService(UserRepository userRepository, SessionService sessionService,
            JwtProvider jwtProvider, KakaoOAuthClient kakaoClient,
            @org.springframework.beans.factory.annotation.Value("${weave.demo-enabled:false}")
            boolean demoEnabled) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.jwtProvider = jwtProvider;
        this.kakaoClient = kakaoClient;
        this.demoEnabled = demoEnabled;
    }

    @Transactional
    public LoginResult loginWithKakao(String code, String redirectUri, String ua, String ip) {
        var profile = kakaoClient.fetchProfile(code, redirectUri);
        User user = userRepository.findByEmail(profile.email())
                .orElseGet(() -> userRepository.save(User.signup(
                        profile.email(), profile.nickname(), profile.profileImage(),
                        User.Provider.KAKAO)));
        return issueSession(user, ua, ip);
    }

    @Transactional
    public LoginResult loginDemo(String ua, String ip) {
        if (!demoEnabled) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        Integer attempts = demoAttempts.get(ip, k -> 0);
        if (attempts >= DEMO_LIMIT_PER_HOUR) {
            throw new BusinessException(ErrorCode.DEMO_RATE_LIMITED);
        }
        demoAttempts.put(ip, attempts + 1);

        User user = userRepository.findByEmail(DEMO_EMAIL)
                // 데모 사용자는 V1 시드 단일 원천 — 온디맨드 생성 경로를 두지 않는다
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return issueSession(user, ua, ip);
    }

    private LoginResult issueSession(User user, String ua, String ip) {
        var issued = sessionService.issue(user.getId(), ua, ip);
        String access = jwtProvider.createAccessToken(user.getId(), DEFAULT_ROLES);
        return new LoginResult(access, issued.refreshToken(), user);
    }

    public record LoginResult(String accessToken, String refreshToken, User user) {
    }
}
