package app.weave.chat.domain;

/**
 * 방 생성 시점의 제안 슬롯 모드 스냅샷 — scheduling의 Mode와 값만 같고 타입은 분리한다.
 * chat은 scheduling을 의존하지 않으므로 자체 enum을 둔다 (chat_rooms.mode CHECK와 동일값).
 */
public enum Mode {
    TIME, DATE
}
