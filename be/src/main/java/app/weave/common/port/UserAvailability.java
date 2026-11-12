package app.weave.common.port;

import java.util.List;

/**
 * 사용자별 추천 입력용 가용성 — scopes는 mode 일치·COMPLETED·범위 겹침 scope만 담는다.
 * useCalendarInference는 preference 행이 없으면 false.
 */
public record UserAvailability(List<AvailabilityScopeInfo> scopes,
        boolean useCalendarInference) {
}
