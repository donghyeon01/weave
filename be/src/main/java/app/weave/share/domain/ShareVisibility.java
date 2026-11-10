package app.weave.share.domain;

/**
 * 공개 범위 4단계 — chk_shares_visibility와 동일 문자열.
 * PRIVATE은 행이 남고 열람만 차단(답글 보존), PUBLIC의 노출 표면은 V2에서 친구 피드로 한정.
 */
public enum ShareVisibility {
    PRIVATE, FRIENDS, GROUP, PUBLIC
}
