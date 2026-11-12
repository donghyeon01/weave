package app.weave.common.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * availability 모듈이 소유한 가용성 데이터의 읽기 전용 포트.
 * 모듈 경계(단방향)상 availability를 볼 수 없는 scheduling이 이 포트로 조회한다 —
 * 구현은 availability 패키지의 어댑터가 담당 (capability-map 조회 포트 규약).
 */
public interface AvailabilityAccess {

    /**
     * mode 일치·COMPLETED·[from,to]와 겹치는 scope+블록 일괄 조회.
     * 맵 키는 요청 userId — 행이 없는 사용자도 빈 scopes의 UserAvailability로 포함한다.
     */
    Map<Long, UserAvailability> availabilityOf(Collection<Long> userIds,
            AvailabilityMode mode, LocalDate from, LocalDate to);
}
