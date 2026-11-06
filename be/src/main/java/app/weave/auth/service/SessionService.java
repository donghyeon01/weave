package app.weave.auth.service;

import app.weave.auth.domain.RefreshToken;
import app.weave.auth.domain.RefreshTokenRepository;
import app.weave.auth.jwt.JwtProvider;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    public static final Duration REFRESH_TTL = Duration.ofDays(14);
    /** 기기당 최대 활성 세션 수 (ADR-0001 개정: 5→4). */
    static final int MAX_SESSIONS = 4;

    private final RefreshTokenRepository repository;
    private final SessionMaintenance maintenance;
    private final JwtProvider jwtProvider;

    public SessionService(RefreshTokenRepository repository, SessionMaintenance maintenance,
            JwtProvider jwtProvider) {
        this.repository = repository;
        this.maintenance = maintenance;
        this.jwtProvider = jwtProvider;
    }

    /** 로그인 시 세션 발급 — 원문 refresh 반환(해시만 DB), 상한 초과 시 소프트 퇴출. */
    @Transactional
    public IssuedSession issue(Long userId, String userAgent, String ip) {
        String raw = jwtProvider.createRefreshToken();
        String hash = TokenHasher.sha256(raw);
        RefreshToken row = repository.save(new RefreshToken(
                userId, hash, userAgent, ip, LocalDateTime.now().plus(REFRESH_TTL)));
        enforceSessionCap(userId);
        return new IssuedSession(raw, row);
    }

    /** 활성(evicted_at IS NULL) 세션이 상한을 넘으면 가장 오래 쓰이지 않은 것부터 evicted 표시. */
    private void enforceSessionCap(Long userId) {
        List<RefreshToken> sessions = repository.findByUserIdOrderByLastAccessedAtAsc(userId);
        long active = sessions.stream().filter(s -> !s.isEvicted()).count();
        for (RefreshToken s : sessions) {
            if (active <= MAX_SESSIONS) {
                break;
            }
            if (!s.isEvicted()) {
                repository.markEvicted(s.getId(), LocalDateTime.now());
                active--;
            }
        }
    }

    /**
     * 회전+판정. 조건부 UPDATE로 원자화 — 동일 해시 동시 요청은 1건만 갱신.
     * 0행 갱신 = 이미 회전된 해시 → previous_token_hash 폴백으로 재사용 판정.
     */
    @Transactional
    public RotatedSession rotateAndValidate(String oldHash, String userAgent) {
        LocalDateTime now = LocalDateTime.now();

        RefreshToken found = repository.findByTokenHash(oldHash).orElse(null);
        if (found != null) {
            if (found.isEvicted()) {
                // 퇴출 신호 1회 전달 후 자기 정리 — 던지는 예외가 본 트랜잭션을 롤백하므로 REQUIRES_NEW로 커밋 보장
                maintenance.deleteSessionHard(found.getId());
                throw new BusinessException(ErrorCode.AUTH_SESSION_EVICTED);
            }
            if (found.getExpiryDate().isBefore(now)) {
                throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
            }
            if (!found.getUserAgent().equals(userAgent)) {
                throw new BusinessException(ErrorCode.AUTH_UA_MISMATCH);
            }
        }

        String newRaw = jwtProvider.createRefreshToken();
        String newHash = TokenHasher.sha256(newRaw);
        int updated = repository.rotate(oldHash, newHash, now.plus(REFRESH_TTL), now);
        if (updated == 1) {
            RefreshToken row = repository.findByTokenHash(newHash).orElseThrow();
            String access = jwtProvider.createAccessToken(row.getUserId(), List.of("ROLE_USER"));
            return new RotatedSession(access, newRaw, row);
        }

        // 0행 — 이미 회전됐거나 존재하지 않는 해시
        var reused = repository.findByPreviousTokenHash(oldHash);
        if (reused.isPresent()) {
            maintenance.deleteSessionHard(reused.get().getId());
            throw new BusinessException(ErrorCode.AUTH_REFRESH_REUSED);
        }
        throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Transactional
    public void revoke(String hash) {
        repository.findByTokenHash(hash).ifPresent(repository::delete);
    }

    public record IssuedSession(String refreshToken, RefreshToken row) {
    }

    public record RotatedSession(String accessToken, String refreshToken, RefreshToken row) {
    }
}
