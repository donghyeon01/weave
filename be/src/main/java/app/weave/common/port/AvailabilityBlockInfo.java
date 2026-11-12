package app.weave.common.port;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자가 "가능"으로 칠한 블록 — DATE 블록은 blockDate만, TIME 블록은 [startAt,endAt)만 채워진다.
 * 추천 판정은 scope 모드에 따라 어느 쪽 필드를 해석할지 결정한다.
 */
public record AvailabilityBlockInfo(LocalDate blockDate,
        LocalDateTime startAt, LocalDateTime endAt) {
}
