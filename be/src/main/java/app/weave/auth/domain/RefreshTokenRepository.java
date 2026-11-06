package app.weave.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByPreviousTokenHash(String previousTokenHash);

    List<RefreshToken> findByUserIdOrderByLastAccessedAtAsc(Long userId);

    long countByUserIdAndEvictedAtIsNull(Long userId);

    /**
     * 회전은 조건부 UPDATE 1쿼리로 원자화 — 동일 해시 동시 요청은 1건만 갱신.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.previousTokenHash = :oldHash, t.tokenHash = :newHash, "
            + "t.expiryDate = :newExpiry, t.lastAccessedAt = :now WHERE t.tokenHash = :oldHash")
    int rotate(@Param("oldHash") String oldHash, @Param("newHash") String newHash,
            @Param("newExpiry") java.time.LocalDateTime newExpiry,
            @Param("now") java.time.LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.evictedAt = :now WHERE t.id = :id")
    int markEvicted(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}
