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

    Optional<Event> findByIdAndUserId(Long id, Long userId);

    Optional<Event> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
