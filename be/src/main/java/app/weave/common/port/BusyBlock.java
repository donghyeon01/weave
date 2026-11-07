package app.weave.common.port;

import java.time.LocalDateTime;

/** 조율 불가 블록 — 친구 일정 미리보기는 시작·종료 시각만 노출한다 (api-contract §3.6). */
public record BusyBlock(LocalDateTime startTime, LocalDateTime endTime) {
}
