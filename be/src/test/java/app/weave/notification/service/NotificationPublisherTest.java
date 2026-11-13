package app.weave.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import app.weave.notification.api.NotificationResponse;
import app.weave.notification.domain.Notification;
import app.weave.notification.domain.NotificationRepository;
import app.weave.notification.service.NotificationPayload.FriendRequest;
import app.weave.notification.service.NotificationPayload.ProposalConfirmed;
import app.weave.notification.service.NotificationPayload.Slot;
import app.weave.notification.service.NotificationPayload.UserRef;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class NotificationPublisherTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    NotificationPublisher publisher;
    @Autowired
    NotificationRepository repository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    SimpMessagingTemplate messagingTemplate;

    private Long userId;

    /** JSON round-trip된 중첩 맵은 LinkedHashMap<String,Object> — 캐스트는 안전하다. */
    private static Map<String, Object> nested(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM users WHERE email = 'pub@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) "
                + "VALUES ('pub@weave.local','P','p','demo')");
        userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email = 'pub@weave.local'", Long.class);
    }

    @Test
    void publish는_호출자_트랜잭션에서_INSERT하고_push는_커밋_후에_나간다() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        tx.execute(status -> {
            publisher.publish(userId,
                    new FriendRequest(11L, new UserRef(2L, "영희")));

            // 같은 트랜잭션의 커넥션에서 INSERT 행이 보인다 — 알림은 트랜잭션 안에서 저장
            Integer inTx = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = ?",
                    Integer.class, userId);
            assertThat(inTx).isEqualTo(1);
            // 커밋 전에는 push가 나가지 않는다
            verifyNoInteractions(messagingTemplate);
            return null;
        });

        // 커밋 후 /user/queue/notifications 로 NotificationResponse 동형 본문이 나간다
        ArgumentCaptor<NotificationResponse> captor =
                ArgumentCaptor.forClass(NotificationResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(userId)), eq("/queue/notifications"), captor.capture());
        NotificationResponse pushed = captor.getValue();
        assertThat(pushed.type()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(pushed.payload()).containsOnlyKeys("friendshipId", "fromUser");
        assertThat(pushed.id()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void type과_payload_record가_불일치하면_거부한다() {
        assertThatThrownBy(() -> publisher.publish(userId, NotificationType.FRIEND_ACCEPTED,
                new FriendRequest(1L, new UserRef(2L, "영희"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void push_실패가_INSERT를_망가뜨리지_않는다() {
        doThrow(new RuntimeException("broker down")).when(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any());

        publisher.publish(userId, new FriendRequest(1L, new UserRef(2L, "영희")));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 계약_type별_payload가_필수_필드를_그대로_저장한다() {
        Slot slot = new Slot.Time(LocalDate.of(2026, 5, 12),
                LocalTime.of(14, 0), LocalTime.of(16, 0));

        publisher.publish(userId, new FriendRequest(7L, new UserRef(2L, "영희")));
        publisher.publish(userId,
                new NotificationPayload.FriendAccepted(7L, new UserRef(3L, "철수")));
        publisher.publish(userId,
                new NotificationPayload.ProposalReceived(9L, "캡스톤 회의",
                        new UserRef(3L, "철수"), slot, 4));
        publisher.publish(userId,
                new NotificationPayload.ProposalAllAccepted(9L, "캡스톤 회의",
                        new UserRef(3L, "철수"), slot, 4));
        publisher.publish(userId, new ProposalConfirmed(9L, "캡스톤 회의", slot, 4));
        publisher.publish(userId,
                new NotificationPayload.ProposalDeclined(9L, "캡스톤 회의",
                        new UserRef(2L, "영희")));
        publisher.publish(userId,
                new NotificationPayload.SharedItem(4L, "DIARY", 12L,
                        new UserRef(3L, "철수"), "제주 여행"));
        publisher.publish(userId,
                new NotificationPayload.ShareReply(4L, 21L, "DIARY",
                        new UserRef(2L, "영희"), "제주 여행"));

        Map<NotificationType, Notification> byType = repository.findAll().stream()
                .collect(Collectors.toMap(Notification::getType, Function.identity()));
        assertThat(byType).containsOnlyKeys(NotificationType.values());

        Map<String, Object> friendRequest = byType.get(NotificationType.FRIEND_REQUEST)
                .getPayload();
        assertThat(friendRequest).containsOnlyKeys("friendshipId", "fromUser");
        assertThat(nested(friendRequest, "fromUser")).containsOnlyKeys("id", "nickname");

        Map<String, Object> friendAccepted = byType.get(NotificationType.FRIEND_ACCEPTED)
                .getPayload();
        assertThat(friendAccepted).containsOnlyKeys("friendshipId", "fromUser");

        Map<String, Object> received = byType.get(NotificationType.PROPOSAL_RECEIVED)
                .getPayload();
        assertThat(received).containsOnlyKeys("proposalId", "title", "organizer", "slot",
                "occurrenceCount");
        assertThat(received).containsEntry("occurrenceCount", 4);
        assertThat(nested(received, "organizer")).containsOnlyKeys("id", "nickname");
        assertThat(nested(received, "slot"))
                .containsOnlyKeys("type", "date", "startTime", "endTime")
                .containsEntry("type", "TIME")
                .containsEntry("startTime", "14:00");

        Map<String, Object> allAccepted = byType.get(NotificationType.PROPOSAL_ALL_ACCEPTED)
                .getPayload();
        assertThat(allAccepted).containsOnlyKeys("proposalId", "title", "organizer", "slot",
                "occurrenceCount");
        assertThat(allAccepted).containsEntry("occurrenceCount", 4);
        assertThat(nested(allAccepted, "organizer")).containsOnlyKeys("id", "nickname");

        Map<String, Object> confirmed = byType.get(NotificationType.PROPOSAL_CONFIRMED)
                .getPayload();
        assertThat(confirmed).containsOnlyKeys("proposalId", "title", "slot",
                "occurrenceCount");
        assertThat(confirmed).containsEntry("occurrenceCount", 4);

        Map<String, Object> declined = byType.get(NotificationType.PROPOSAL_DECLINED)
                .getPayload();
        assertThat(declined).containsOnlyKeys("proposalId", "title", "declinedBy");
        assertThat(nested(declined, "declinedBy")).containsOnlyKeys("id", "nickname");

        Map<String, Object> sharedItem = byType.get(NotificationType.SHARED_ITEM)
                .getPayload();
        assertThat(sharedItem)
                .containsOnlyKeys("shareId", "itemType", "itemId", "owner", "itemTitle");
        assertThat(nested(sharedItem, "owner")).containsOnlyKeys("id", "nickname");

        Map<String, Object> shareReply = byType.get(NotificationType.SHARE_REPLY)
                .getPayload();
        assertThat(shareReply)
                .containsOnlyKeys("shareId", "replyId", "itemType", "author", "itemTitle");
        assertThat(nested(shareReply, "author")).containsOnlyKeys("id", "nickname");

        // DATE 슬롯도 계약 §3.8 형태 — {type,startDate,endDate}
        assertThat(new Slot.Days(LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 14))
                .toMap())
                .containsOnlyKeys("type", "startDate", "endDate")
                .containsEntry("type", "DATE");
    }

    @Test
    void userId_payload_편의_오버로드도_동일하게_저장한다() {
        publisher.publish(userId, new ProposalConfirmed(5L, "저녁 약속",
                new Slot.Days(LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 14)), 1));

        List<Notification> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getType()).isEqualTo(NotificationType.PROPOSAL_CONFIRMED);
        assertThat(all.get(0).getPayload().get("slot"))
                .isEqualTo(Map.of("type", "DATE",
                        "startDate", "2026-05-12", "endDate", "2026-05-14"));
    }
}
