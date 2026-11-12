package app.weave.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.auth.domain.RefreshToken;
import app.weave.auth.domain.RefreshTokenRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SessionServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    SessionService sessionService;
    @Autowired
    RefreshTokenRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users WHERE email = 't@weave.local'");
        jdbc.update("INSERT INTO users(email, name, nickname, provider) VALUES ('t@weave.local', 'T', 't', 'demo')");
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 't@weave.local'", Long.class);
    }

    @Test
    void 회전하면_구_해시가_previous로_이동하고_last_accessed가_갱신된다() {
        var issued = sessionService.issue(userId, "ua", "1.1.1.1");
        String oldHash = TokenHasher.sha256(issued.refreshToken());

        var rotated = sessionService.rotateAndValidate(oldHash, "ua");

        RefreshToken row = repository.findByTokenHash(TokenHasher.sha256(rotated.refreshToken()))
                .orElseThrow();
        assertThat(row.getPreviousTokenHash()).isEqualTo(oldHash);
        assertThat(row.getLastAccessedAt()).isNotNull();
        assertThat(rotated.accessToken()).isNotBlank();
    }

    @Test
    void 유예_내_구_해시_재제시는_ROTATED로_세션을_유지한다() {
        var issued = sessionService.issue(userId, "ua", "1.1.1.1");
        String oldHash = TokenHasher.sha256(issued.refreshToken());
        sessionService.rotateAndValidate(oldHash, "ua");

        // 회전 직후(유예 10초 이내)의 구 해시 재제시 — 페이지 리로드·재시도 경합으로 간주
        assertThatThrownBy(() -> sessionService.rotateAndValidate(oldHash, "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REFRESH_ROTATED);

        // 세션 행은 삭제되지 않는다 — 새 해시로 계속 갱신 가능해야 함
        assertThat(repository.findByPreviousTokenHash(oldHash)).isPresent();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 유예_경과_후_구_해시_재제시는_재사용_탐지로_행_삭제가_커밋으로_유지된다() {
        var issued = sessionService.issue(userId, "ua", "1.1.1.1");
        String oldHash = TokenHasher.sha256(issued.refreshToken());
        sessionService.rotateAndValidate(oldHash, "ua");
        // 회전 시각을 유예(10초) 이전으로 되돌려 탈취 재제시 상황을 재현한다
        jdbc.update("UPDATE refresh_tokens SET last_accessed_at = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()
                        .minus(SessionService.REFRESH_REUSE_LEEWAY).minusSeconds(1)));

        assertThatThrownBy(() -> sessionService.rotateAndValidate(oldHash, "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REFRESH_REUSED);

        // REQUIRES_NEW 삭제가 본 예외 롤백과 무관하게 커밋됐는지
        assertThat(repository.findByPreviousTokenHash(oldHash)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void revoke는_구_해시로도_세션_행을_삭제한다() {
        var issued = sessionService.issue(userId, "ua", "1.1.1.1");
        String oldHash = TokenHasher.sha256(issued.refreshToken());
        sessionService.rotateAndValidate(oldHash, "ua");

        // 로그아웃 요청이 회전과 경합해 구 해시만 들고 온 경우 — 세션 행이 남으면 안 된다
        sessionService.revoke(oldHash);

        assertThat(repository.count()).isZero();
    }

    @Test
    void 어느_해시에도_없는_토큰은_INVALID() {
        assertThatThrownBy(() -> sessionService.rotateAndValidate(
                TokenHasher.sha256("nonexistent"), "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Test
    void 상한_4개_초과시_최소_접근_세션이_퇴출되고_퇴출_해시는_EVICTED후_삭제된다() {
        // 5개 발급 — 첫 발급 세션이 상한 초과로 퇴출돼야 함
        var first = sessionService.issue(userId, "ua", "1.1.1.1");
        for (int i = 0; i < 4; i++) {
            sessionService.issue(userId, "ua", "1.1.1.1");
        }

        String firstHash = TokenHasher.sha256(first.refreshToken());
        assertThat(repository.findByTokenHash(firstHash).orElseThrow().isEvicted()).isTrue();

        assertThatThrownBy(() -> sessionService.rotateAndValidate(firstHash, "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_SESSION_EVICTED);

        // 1회 신호 후 행 삭제
        assertThat(repository.findByTokenHash(firstHash)).isEmpty();
        // 활성 세션은 4개 유지
        assertThat(repository.countByUserIdAndEvictedAtIsNull(userId)).isEqualTo(4);
    }

    @Test
    void user_agent_불일치는_갱신을_거부한다() {
        var issued = sessionService.issue(userId, "chrome", "1.1.1.1");

        assertThatThrownBy(() -> sessionService.rotateAndValidate(
                TokenHasher.sha256(issued.refreshToken()), "firefox"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_UA_MISMATCH);
    }

    @Test
    void 동일_토큰_동시_회전은_1건만_성공하고_승자_세션이_살아남는다() throws InterruptedException {
        var issued = sessionService.issue(userId, "ua", "1.1.1.1");
        String hash = TokenHasher.sha256(issued.refreshToken());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        AtomicReference<SessionService.RotatedSession> winner = new AtomicReference<>();

        Runnable attempt = () -> {
            ready.countDown();
            try {
                ready.await(2, TimeUnit.SECONDS);
                winner.compareAndSet(null, sessionService.rotateAndValidate(hash, "ua"));
                success.incrementAndGet();
            } catch (BusinessException e) {
                failure.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        pool.submit(attempt);
        pool.submit(attempt);
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);

        // 패자의 구 해시 재제시는 유예 안이므로 ROTATED — 승자 세션이 삭제되면 안 된다
        assertThat(repository.count()).isEqualTo(1);
        // 승자가 받은 새 해시로 다시 회전 가능해야 한다 (T2 생존 증명)
        String newHash = TokenHasher.sha256(winner.get().refreshToken());
        var rotated = sessionService.rotateAndValidate(newHash, "ua");
        assertThat(rotated.accessToken()).isNotBlank();
    }
}
