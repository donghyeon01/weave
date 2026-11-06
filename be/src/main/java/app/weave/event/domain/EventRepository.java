package app.weave.event.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByUserId(Long userId);

    /** range 겹침 조회 — start < endExclusive AND end > startExclusive. */
    List<Event> findByUserIdAndStartTimeLessThanAndEndTimeGreaterThan(
            Long userId, LocalDateTime endExclusive, LocalDateTime startExclusive);

    /** 조율 추천의 다중 참여자 busy 로드 — 위와 같은 겹침 조건의 IN 버전. */
    List<Event> findByUserIdInAndStartTimeLessThanAndEndTimeGreaterThan(
            List<Long> userIds, LocalDateTime endExclusive, LocalDateTime startExclusive);

    Optional<Event> findByIdAndUserId(Long id, Long userId);

    Optional<Event> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
