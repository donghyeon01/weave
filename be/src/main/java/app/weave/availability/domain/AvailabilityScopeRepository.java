package app.weave.availability.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityScopeRepository extends JpaRepository<AvailabilityScope, Long> {

    Optional<AvailabilityScope> findByIdAndUserId(Long id, Long userId);

    /** FOR UPDATE 잠금 — 소유권을 조건에 포함시켜 타인 행을 잠그지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AvailabilityScope s where s.id = :id and s.userId = :userId")
    Optional<AvailabilityScope> findByIdAndUserIdForUpdate(@Param("id") Long id,
            @Param("userId") Long userId);

    /** 같은 user+mode의 활성(INPUT|COMPLETED) 범위와 기간 겹침 여부 — 양끝 포함 날짜. */
    @Query("select count(s) > 0 from AvailabilityScope s where s.userId = :userId"
            + " and s.mode = :mode and s.status in :activeStatuses"
            + " and s.rangeStart <= :rangeEnd and s.rangeEnd >= :rangeStart")
    boolean existsOverlappingActive(@Param("userId") Long userId,
            @Param("mode") ScopeMode mode,
            @Param("activeStatuses") Collection<ScopeStatus> activeStatuses,
            @Param("rangeStart") LocalDate rangeStart, @Param("rangeEnd") LocalDate rangeEnd);

    /** from/to는 각각 선택 — 한쪽만 주면 그 경계만 적용, 둘 다 생략 시 전체. */
    @Query("select s from AvailabilityScope s where s.userId = :userId"
            + " and (:to is null or s.rangeStart <= :to)"
            + " and (:from is null or s.rangeEnd >= :from)"
            + " order by s.rangeStart, s.id")
    List<AvailabilityScope> findByUserIdOverlapping(@Param("userId") Long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
