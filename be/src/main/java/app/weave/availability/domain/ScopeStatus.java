package app.weave.availability.domain;

/**
 * 범위 상태 — INPUT(작성 중) / COMPLETED(제출 완료, 추천 입력으로 소비) / CANCELLED.
 * CANCELLED는 예약 상태값 — 현 계약의 DELETE는 물리 삭제라 생성 경로가 없다 (api-contract §3.12).
 */
public enum ScopeStatus {
    INPUT, COMPLETED, CANCELLED
}
