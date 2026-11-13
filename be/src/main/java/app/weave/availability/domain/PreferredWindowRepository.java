package app.weave.availability.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferredWindowRepository extends JpaRepository<PreferredWindow, Long> {

    List<PreferredWindow> findByUserIdOrderByDayOfWeekAscStartTimeAsc(Long userId);

    /** scheduling의 preferenceScore 일괄 조회용 — user_id 인덱스로 한 쿼리. */
    List<PreferredWindow> findByUserIdIn(Collection<Long> userIds);

    void deleteByUserId(Long userId);
}
