package app.weave.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 이메일 정확 일치 검색 — 본인 제외 (친구 요청 대상 찾기). */
    Optional<User> findByEmailAndIdNot(String email, Long id);
}
