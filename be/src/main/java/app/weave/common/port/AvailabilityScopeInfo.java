package app.weave.common.port;

import java.time.LocalDate;
import java.util.List;

/**
 * COMPLETED 상태의 가용성 범위 — [rangeStart, rangeEnd]는 양끝 포함 날짜 범위.
 * blocks는 이 scope에 귀속된 블록만 담는다.
 */
public record AvailabilityScopeInfo(LocalDate rangeStart, LocalDate rangeEnd,
        List<AvailabilityBlockInfo> blocks) {
}
