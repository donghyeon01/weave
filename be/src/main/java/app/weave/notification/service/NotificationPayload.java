package app.weave.notification.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * api-contract §3.7 type·payload 계약의 타입 안전 표현.
 * 발행 측(friend·scheduling)이 이 record로만 직렬화한다 — 임의 Map 주입 경로를 두지 않는다 (db-schema §9).
 * 각 record의 type()과 필드 집합은 계약 표와 1:1 대응한다.
 */
public sealed interface NotificationPayload {

    NotificationType type();

    /** 계약 §3.7 형태의 JSON 필드 맵 — notifications.payload 컬럼과 WS 본문에 그대로 들어간다. */
    Map<String, Object> toMap();

    /** 알림 카드가 렌더하는 상대방 표시 정보 — {"id","nickname"}. */
    record UserRef(Long id, String nickname) {
        Map<String, Object> toMap() {
            return Map.of("id", id, "nickname", nickname);
        }
    }

    /** 계약 §3.8 Slot — scheduling이 TIME/DATE 구조를 확정해 주입한다. */
    sealed interface Slot {
        DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

        Map<String, Object> toMap();

        record Time(LocalDate date, LocalTime startTime, LocalTime endTime) implements Slot {
            @Override
            public Map<String, Object> toMap() {
                return Map.of("type", "TIME", "date", date.toString(),
                        "startTime", startTime.format(HH_MM), "endTime", endTime.format(HH_MM));
            }
        }

        record Days(LocalDate startDate, LocalDate endDate) implements Slot {
            @Override
            public Map<String, Object> toMap() {
                return Map.of("type", "DATE", "startDate", startDate.toString(),
                        "endDate", endDate.toString());
            }
        }
    }

    record FriendRequest(Long friendshipId, UserRef fromUser) implements NotificationPayload {
        @Override
        public NotificationType type() {
            return NotificationType.FRIEND_REQUEST;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("friendshipId", friendshipId, "fromUser", fromUser.toMap());
        }
    }

    record FriendAccepted(Long friendshipId, UserRef fromUser) implements NotificationPayload {
        @Override
        public NotificationType type() {
            return NotificationType.FRIEND_ACCEPTED;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("friendshipId", friendshipId, "fromUser", fromUser.toMap());
        }
    }

    record ProposalReceived(Long proposalId, String title, UserRef organizer, Slot slot)
            implements NotificationPayload {
        @Override
        public NotificationType type() {
            return NotificationType.PROPOSAL_RECEIVED;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("proposalId", proposalId, "title", title,
                    "organizer", organizer.toMap(), "slot", slot.toMap());
        }
    }

    record ProposalConfirmed(Long proposalId, String title, Slot slot)
            implements NotificationPayload {
        @Override
        public NotificationType type() {
            return NotificationType.PROPOSAL_CONFIRMED;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("proposalId", proposalId, "title", title, "slot", slot.toMap());
        }
    }

    record ProposalDeclined(Long proposalId, String title, UserRef declinedBy)
            implements NotificationPayload {
        @Override
        public NotificationType type() {
            return NotificationType.PROPOSAL_DECLINED;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("proposalId", proposalId, "title", title,
                    "declinedBy", declinedBy.toMap());
        }
    }
}
