package app.weave.auth.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** FOR UPDATE 잠금 — 사용자 단위 직렬화가 필요한 check-then-insert의 잠금 행으로 쓴다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** 이메일 정확 일치 검색 — 본인 제외 (친구 요청 대상 찾기). */
    Optional<User> findByEmailAndIdNot(String email, Long id);
}
