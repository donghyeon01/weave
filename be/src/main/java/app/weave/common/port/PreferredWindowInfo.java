package app.weave.common.port;

import java.time.LocalTime;

/**
 * 사용자별 선호 시간대 — dayOfWeek는 ISO 요일(1=월~7=일),
 * [startTime, endTime)는 하루 안의 시간 구간. scheduling의 preferenceScore 계산 입력.
 */
public record PreferredWindowInfo(int dayOfWeek, LocalTime startTime,
        LocalTime endTime) {
}
