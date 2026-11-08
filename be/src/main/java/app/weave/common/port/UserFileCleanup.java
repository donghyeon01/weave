package app.weave.common.port;

/**
 * 사용자 소유 파일(업로드 산출물)의 정리 출구 — 탈퇴 시 FK CASCADE가 DB 행만 지우므로
 * 디스크 파일은 이 포트로 정리한다. 구현은 파일을 소유한 feature가 제공한다.
 */
public interface UserFileCleanup {

    void deleteAllForUser(Long userId);
}
