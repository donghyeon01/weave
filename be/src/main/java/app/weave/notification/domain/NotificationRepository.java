package app.weave.notification.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 소유권 포함 조회 — 타인 알림 접근은 404로 자연 반환 (api-contract §1.3 IDOR 방지). */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * 미읽음 우선 → createdAt 내림차순 단일 페이지 쿼리.
     * NULL 정렬은 DB마다 달라 CASE로 명시한다 — 두 쿼리 머지 방식은 페이지 경계가 깨진다.
     */
    @Query(value = "SELECT n FROM Notification n WHERE n.userId = :userId "
            + "ORDER BY CASE WHEN n.readAt IS NULL THEN 0 ELSE 1 END, n.createdAt DESC",
            countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId")
    Page<Notification> findPageByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now "
            + "WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
