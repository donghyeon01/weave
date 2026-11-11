package app.weave.common.port;

import java.util.Optional;

/**
 * diary 모듈이 소유한 일기 데이터의 읽기 전용 포트.
 * share 모듈이 diary 엔터티·테이블을 모르고 조회하는 출구 — 구현은 diary 패키지의 어댑터가
 * 담당한다 (capability-map 조회 포트 규약, ADR-0009).
 */
public interface DiaryAccess {

    /** 소유 검증 포함 일기 조회 — 미존재·타인 소유 모두 empty (IDOR 방지). */
    Optional<SharedDiary> diaryOf(Long ownerId, Long diaryId);

    /** 소유 검증 포함 사진 바이트 — 미존재·타인 소유 모두 empty. */
    Optional<SharedPhotoBytes> photoBytesOf(Long ownerId, Long diaryId, Long photoId);
}
