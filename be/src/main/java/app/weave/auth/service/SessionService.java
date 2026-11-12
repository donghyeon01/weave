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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    public static final Duration REFRESH_TTL = Duration.ofDays(14);
    /** 기기당 최대 활성 세션 수 (ADR-0001 개정: 5→4). */
    static final int MAX_SESSIONS = 4;
    /**
     * 회전 유예 — previous_token_hash로 폐기된 해시의 재제시를 이 시간 안에서는
     * 경합(페이지 리로드·재시도)으로 보고 세션을 유지한다. 경과 후 재제시만 탈취로 폐기.
     */
    static final Duration REFRESH_REUSE_LEEWAY = Duration.ofSeconds(10);

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
     * READ COMMITTED: UPDATE 0행 후의 재판정 SELECT가 승자 커밋을 봐야
     * REPEATABLE READ 스냅샷이 재사용 오판·오분류를 만들지 않는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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

        // UPDATE 0행 — 사전 조회와 UPDATE 사이에 퇴출·만료됐거나 이미 회전된 해시.
        // 최신 상태로 실패 사유를 재분류한다 (READ COMMITTED라 승자 커밋이 보인다).
        RefreshToken current = repository.findByTokenHash(oldHash).orElse(null);
        if (current != null) {
            // UPDATE 가드(evicted·expiry)에 걸린 경우 — 여기 도달한 경합 패자에게 현재 상태를 알린다
            if (current.isEvicted()) {
                maintenance.deleteSessionHard(current.getId());
                throw new BusinessException(ErrorCode.AUTH_SESSION_EVICTED);
            }
            throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        var reused = repository.findByPreviousTokenHash(oldHash);
        if (reused.isPresent()) {
            RefreshToken row = reused.get();
            // previous_token_hash는 1세대만 보관하므로 lastAccessedAt이 곧 이 해시의 폐기 시각.
            // 유예 안의 재제시는 같은 클라이언트의 경합일 수 있어 세션을 유지하고 재시도만 유도한다.
            if (row.getLastAccessedAt().isAfter(now.minus(REFRESH_REUSE_LEEWAY))) {
                throw new BusinessException(ErrorCode.AUTH_REFRESH_ROTATED);
            }
            maintenance.deleteSessionHard(row.getId());
            throw new BusinessException(ErrorCode.AUTH_REFRESH_REUSED);
        }
        throw new BusinessException(ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Transactional
    public void revoke(String hash) {
        // 로그아웃이 회전과 경합하면 구 해시만 남는다 — previous까지 매칭해 세션 행을 지운다.
        repository.findByTokenHash(hash)
                .or(() -> repository.findByPreviousTokenHash(hash))
                .ifPresent(repository::delete);
    }

    public record IssuedSession(String refreshToken, RefreshToken row) {
    }

    public record RotatedSession(String accessToken, String refreshToken, RefreshToken row) {
    }
}
