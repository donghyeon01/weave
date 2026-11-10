package app.weave.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.chat.domain.ChatMessageRepository;
import app.weave.chat.domain.ChatRoom;
import app.weave.chat.domain.ChatRoomMember;
import app.weave.chat.domain.ChatRoomMemberRepository;
import app.weave.chat.domain.ChatRoomRepository;
import app.weave.chat.domain.Mode;
import app.weave.chat.service.ChatService.ChatMessageView;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ChatServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    ChatService chatService;
    @Autowired
    ChatRoomRepository roomRepository;
    @Autowired
    ChatRoomMemberRepository memberRepository;
    @Autowired
    ChatMessageRepository messageRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long p1Id;
    private Long outsiderId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM chat_messages");
        jdbc.update("DELETE FROM chat_room_members");
        jdbc.update("DELETE FROM chat_rooms");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('p1@weave.local','일','일','demo'),"
                + "('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        p1Id = idOf("p1@weave.local");
        outsiderId = idOf("out@weave.local");
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private ChatRoom newRoom(long proposalId, List<Long> members) {
        return chatService.createRoom(proposalId, "방" + proposalId, Mode.TIME,
                LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 1, 12, 0),
                members);
    }

    private void assertError(ErrorCode code, Runnable r) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    void createRoom_방과_멤버_슬롯_스냅샷이_저장된다() {
        ChatRoom room = newRoom(1L, List.of(meId, p1Id));

        assertThat(roomRepository.findByProposalId(1L)).isPresent();
        assertThat(memberRepository.findByRoomId(room.getId())).hasSize(2);
        assertThat(room.getMode()).isEqualTo(Mode.TIME);
        assertThat(room.getStartTime()).isEqualTo(LocalDateTime.of(2026, 12, 1, 10, 0));
        assertThat(room.getStatus()).isEqualTo(ChatRoom.Status.OPEN);
    }

    @Test
    void createRoom_같은_proposalId_재생성은_UK_위반() {
        newRoom(2L, List.of(meId));
        assertThatThrownBy(() -> newRoom(2L, List.of(meId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 비멤버와_미존재_방은_404() {
        ChatRoom room = newRoom(3L, List.of(meId));

        assertError(ErrorCode.CHAT_NOT_MEMBER,
                () -> chatService.history(outsiderId, room.getId(), null, null));
        assertError(ErrorCode.CHAT_NOT_MEMBER,
                () -> chatService.send(outsiderId, room.getId(), "hi", null));
        assertError(ErrorCode.CHAT_NOT_MEMBER,
                () -> chatService.markRead(outsiderId, room.getId(), 1L));
        assertError(ErrorCode.CHAT_ROOM_NOT_FOUND,
                () -> chatService.history(meId, 999999L, null, null));
        assertThat(chatService.isMember(room.getId(), outsiderId)).isFalse();
        assertThat(chatService.isMember(room.getId(), meId)).isTrue();
    }

    @Test
    void CLOSED_방은_송신만_409이고_이력_읽음은_허용된다() {
        ChatRoom room = newRoom(4L, List.of(meId));
        ChatMessageView m = chatService.send(meId, room.getId(), "hi", null);
        chatService.closeRoom(4L);

        assertError(ErrorCode.CHAT_ROOM_CLOSED,
                () -> chatService.send(meId, room.getId(), "again", null));
        // 읽기 전용 — 이력 조회·읽음 커서 갱신은 멤버에게 허용
        assertThat(chatService.history(meId, room.getId(), null, null))
                .extracting(ChatMessageView::id).containsExactly(m.id());
        assertThat(chatService.markRead(meId, room.getId(), m.id()).getLastReadMessageId())
                .isEqualTo(m.id());
        assertThat(roomRepository.findById(room.getId()).get().getClosedAt()).isNotNull();
    }

    @Test
    void send_멱등과_검증() {
        ChatRoom room = newRoom(5L, List.of(meId));

        ChatMessageView first = chatService.send(meId, room.getId(), "hello", "c-1");
        ChatMessageView retry = chatService.send(meId, room.getId(), "hello", "c-1");
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(chatService.history(meId, room.getId(), null, null)).hasSize(1);

        // null·빈 문자열 키는 멱등 미적용 — 매번 새 행
        chatService.send(meId, room.getId(), "a", null);
        chatService.send(meId, room.getId(), "b", "");
        chatService.send(meId, room.getId(), "c", "   ");
        assertThat(chatService.history(meId, room.getId(), null, null)).hasSize(4);

        assertError(ErrorCode.CHAT_MESSAGE_INVALID,
                () -> chatService.send(meId, room.getId(), "  ", null));
        assertError(ErrorCode.CHAT_MESSAGE_INVALID,
                () -> chatService.send(meId, room.getId(), "x".repeat(2001), null));
        assertError(ErrorCode.CHAT_MESSAGE_INVALID,
                () -> chatService.send(meId, room.getId(), "ok", "k".repeat(65)));
        // trim 적용
        assertThat(chatService.send(meId, room.getId(), "  hi  ", null).content())
                .isEqualTo("hi");
    }

    @Test
    void 같은_clientMessageId는_sender별로_독립이다() {
        ChatRoom room = newRoom(11L, List.of(meId, p1Id));

        ChatMessageView mine = chatService.send(meId, room.getId(), "내 메시지", "shared-key");
        // 다른 멤버가 같은 키를 써도 내 행이 아니라 본인 행이 생성된다 (선점 버그 회귀)
        ChatMessageView theirs = chatService.send(p1Id, room.getId(), "상대 메시지", "shared-key");
        assertThat(theirs.id()).isNotEqualTo(mine.id());
        assertThat(theirs.senderId()).isEqualTo(p1Id);

        // 각자의 재전송은 각자의 행을 반환한다
        assertThat(chatService.send(meId, room.getId(), "다시", "shared-key").id())
                .isEqualTo(mine.id());
        assertThat(chatService.send(p1Id, room.getId(), "다시", "shared-key").id())
                .isEqualTo(theirs.id());
        assertThat(chatService.history(meId, room.getId(), null, null)).hasSize(2);
    }

    @Test
    void history_커서_경계와_오름차순() {
        ChatRoom room = newRoom(6L, List.of(meId));
        for (int i = 1; i <= 5; i++) {
            chatService.send(meId, room.getId(), "m" + i, null);
        }

        List<ChatMessageView> firstPage = chatService.history(meId, room.getId(), null, 2);
        assertThat(firstPage).extracting(ChatMessageView::content).containsExactly("m4", "m5");

        List<ChatMessageView> secondPage =
                chatService.history(meId, room.getId(), firstPage.get(0).id(), 2);
        assertThat(secondPage).extracting(ChatMessageView::content)
                .containsExactly("m2", "m3");

        assertError(ErrorCode.COMMON_VALIDATION,
                () -> chatService.history(meId, room.getId(), null, 0));
        assertError(ErrorCode.COMMON_VALIDATION,
                () -> chatService.history(meId, room.getId(), null, 101));
    }

    @Test
    void send_뷰에_nickname이_채워진다() {
        ChatRoom room = newRoom(7L, List.of(meId));
        ChatMessageView v = chatService.send(meId, room.getId(), "hi", null);
        assertThat(v.nickname()).isEqualTo("나");
        assertThat(v.senderId()).isEqualTo(meId);
    }

    @Test
    void unread와_markRead_규칙() {
        ChatRoom room = newRoom(8L, List.of(meId, p1Id));
        ChatMessageView m1 = chatService.send(p1Id, room.getId(), "p1-1", null);
        chatService.send(meId, room.getId(), "me", null); // 본인 발신은 미읽음 제외
        ChatMessageView m3 = chatService.send(p1Id, room.getId(), "p1-2", null);

        // last_read NULL → 상대 발신 전체가 미읽음
        var rooms = chatService.myRooms(meId);
        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).unreadCount()).isEqualTo(2);
        assertThat(rooms.get(0).slot().mode()).isEqualTo(Mode.TIME);
        // 목록 카드 메타 — 최신 발신 내용·시각·멤버 수
        // lastMessageAt은 DB 재조회 값이라 send 직후 뷰의 나노초와 다를 수 있어 리포지토리 값과 비교한다
        assertThat(rooms.get(0).lastMessage()).isEqualTo("p1-2");
        assertThat(rooms.get(0).lastMessageAt())
                .isEqualTo(messageRepository.findById(m3.id()).get().getCreatedAt());
        assertThat(rooms.get(0).memberCount()).isEqualTo(2);

        // 타 방 메시지 id는 400
        ChatRoom other = newRoom(9L, List.of(meId, p1Id));
        ChatMessageView foreign = chatService.send(p1Id, other.getId(), "x", null);
        assertError(ErrorCode.CHAT_MESSAGE_INVALID,
                () -> chatService.markRead(meId, room.getId(), foreign.id()));

        // markRead 후 0 — room 9가 생겼으므로 해당 방만 골라 확인
        ChatRoomMember member = chatService.markRead(meId, room.getId(), m3.id());
        assertThat(member.getLastReadMessageId()).isEqualTo(m3.id());
        assertThat(chatService.myRooms(meId).stream()
                .filter(s -> s.roomId().equals(room.getId())).findFirst().get().unreadCount())
                .isEqualTo(0);

        // 커서 역행은 no-op — 단조 증가
        ChatRoomMember after = chatService.markRead(meId, room.getId(), m1.id());
        assertThat(after.getLastReadMessageId()).isEqualTo(m3.id());
    }

    @Test
    void closeRoom은_멱등_no_op() {
        chatService.closeRoom(4242L); // 방 없음 — 예외 없이 no-op

        ChatRoom room = newRoom(10L, List.of(meId));
        chatService.closeRoom(10L);
        LocalDateTime first = roomRepository.findById(room.getId()).get().getClosedAt();
        chatService.closeRoom(10L); // 재close — 상태 유지
        assertThat(roomRepository.findById(room.getId()).get().getStatus())
                .isEqualTo(ChatRoom.Status.CLOSED);
        assertThat(first).isNotNull();
        // CLOSED 방도 목록에 status=CLOSED로 노출 (읽기 전용)
        assertThat(chatService.myRooms(meId)).hasSize(1);
        assertThat(chatService.myRooms(meId).get(0).status())
                .isEqualTo(ChatRoom.Status.CLOSED);
        // 메시지 없는 방 — lastMessage·lastMessageAt은 null, memberCount는 멤버 수
        assertThat(chatService.myRooms(meId).get(0).lastMessage()).isNull();
        assertThat(chatService.myRooms(meId).get(0).lastMessageAt()).isNull();
        assertThat(chatService.myRooms(meId).get(0).memberCount()).isEqualTo(1);
    }

    /** 두 작업을 같은 latch에서 동시에 놓아 경합을 유도한다. */
    private <T> List<T> race(Callable<T> t1, Callable<T> t2) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<T> f1 = pool.submit(() -> { ready.countDown(); go.await(); return t1.call(); });
        Future<T> f2 = pool.submit(() -> { ready.countDown(); go.await(); return t2.call(); });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        List<T> results = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
        pool.shutdownNow();
        return results;
    }

    @Test
    void 같은_clientMessageId_동시_송신은_둘다_성공하고_같은_행을_반환한다() throws Exception {
        ChatRoom room = newRoom(20L, List.of(meId));
        // check-then-INSERT의 미스 구간은 좁아 라운드를 반복해 경합 노출을 높인다
        for (int round = 0; round < 10; round++) {
            final int r = round;
            String key = "dup-" + r;
            List<ChatMessageView> results = race(
                    () -> chatService.send(meId, room.getId(), "a" + r, key),
                    () -> chatService.send(meId, room.getId(), "a" + r, key));
            assertThat(results.get(0).id()).isEqualTo(results.get(1).id());
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_messages WHERE room_id=? "
                        + "AND client_message_id LIKE 'dup-%'",
                Integer.class, room.getId())).isEqualTo(10);
    }

    @Test
    void 동시_markRead는_커서가_역행하지_않고_큰_값이_남는다() throws Exception {
        ChatRoom room = newRoom(21L, List.of(meId, p1Id));
        ChatMessageView lo = chatService.send(p1Id, room.getId(), "lo", null);
        ChatMessageView hi = chatService.send(p1Id, room.getId(), "hi", null);
        for (int round = 0; round < 5; round++) {
            jdbc.update("UPDATE chat_room_members SET last_read_message_id=NULL "
                    + "WHERE room_id=? AND user_id=?", room.getId(), meId);
            race(() -> chatService.markRead(meId, room.getId(), hi.id()),
                    () -> chatService.markRead(meId, room.getId(), lo.id()));
            Long cursor = jdbc.queryForObject(
                    "SELECT last_read_message_id FROM chat_room_members "
                            + "WHERE room_id=? AND user_id=?",
                    Long.class, room.getId(), meId);
            assertThat(cursor).isEqualTo(hi.id());
        }
    }

    @Test
    void send와_closeRoom의_경합은_직렬화되어_결과가_일관된다() throws Exception {
        for (int round = 0; round < 8; round++) {
            long proposalId = 30L + round;
            ChatRoom room = newRoom(proposalId, List.of(meId));
            final int r = round;
            List<Object> results = race(
                    () -> {
                        try {
                            return chatService.send(meId, room.getId(), "r" + r, null);
                        } catch (BusinessException e) {
                            return e;
                        }
                    },
                    () -> {
                        chatService.closeRoom(proposalId);
                        return "closed";
                    });
            Object sendResult = results.get(0);
            Integer msgCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM chat_messages WHERE room_id=?",
                    Integer.class, room.getId());
            if (sendResult instanceof BusinessException e) {
                // close 선점 — send는 CHAT-ROOM-CLOSED고 메시지는 없어야 한다
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);
                assertThat(msgCount).isEqualTo(0);
            } else {
                // send 선점 — 메시지 저장 후 방은 CLOSED로 전이
                assertThat(msgCount).isEqualTo(1);
            }
            assertThat(roomRepository.findById(room.getId()).get().getStatus())
                    .isEqualTo(ChatRoom.Status.CLOSED);
        }
    }

    @Test
    void 동시_송신_후_history는_createdAt_오름차순을_보장한다() throws Exception {
        ChatRoom room = newRoom(40L, List.of(meId, p1Id));
        for (int i = 0; i < 4; i++) {
            final int n = i;
            race(() -> chatService.send(meId, room.getId(), "me-" + n, null),
                    () -> chatService.send(p1Id, room.getId(), "p1-" + n, null));
        }
        List<ChatMessageView> page = chatService.history(meId, room.getId(), null, null);
        assertThat(page).hasSize(8);
        assertThat(page).isSortedAccordingTo(Comparator
                .comparing(ChatMessageView::createdAt).thenComparing(ChatMessageView::id));
    }
}
