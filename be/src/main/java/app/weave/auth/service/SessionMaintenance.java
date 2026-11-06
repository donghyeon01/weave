package app.weave.auth.service;

import app.weave.auth.domain.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈취 판정 세션 삭제는 본 트랜잭션 롤백과 무관하게 커밋돼야 한다 —
 * 직후 던지는 REUSED 예외가 삭제를 되돌리면 안 된다.
 */
@Component
public class SessionMaintenance {

    private final RefreshTokenRepository repository;

    public SessionMaintenance(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteSessionHard(Long id) {
        repository.deleteById(id);
    }
}
