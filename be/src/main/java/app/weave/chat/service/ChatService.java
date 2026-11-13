package app.weave.chat.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.chat.domain.ChatMessage;
import app.weave.chat.domain.ChatRoom;
import app.weave.chat.domain.ChatRoom.Status;
import app.weave.chat.domain.ChatRoomMember;
import app.weave.chat.domain.ChatRoomMemberRepository;
import app.weave.chat.domain.ChatRoomRepository;
import app.weave.chat.domain.ChatMessageRepository;
import app.weave.chat.domain.Mode;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 제안 범위 단체채팅 — scheduling이 방 생성·종료를 호출하는 단방향 계약의 수신 측.
 * chat은 scheduling을 모른다 (proposal_id는 논리 참조, 슬롯·멤버는 스냅샷).
 * 에러 우선순위: 방 미존재(404) → 비멤버(404, 소유권 규약상 403 아님) → CLOSED(409, 송신만) → 입력 검증.
 * CLOSED 방은 읽기 전용 — 목록·이력·읽음 커서는 멤버에게 허용하고 송신만 409로 거부한다.
 */
@Service
public class ChatService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_CONTENT = 2000;
    private static final int MAX_CLIENT_MESSAGE_ID = 64;

    /**
     * 방 목록 항목 — slot은 생성 시 스냅샷 그대로. status로 CLOSED 읽기 전용을 구분한다.
     * lastMessage·lastMessageAt·memberCount는 목록 카드 프리뷰·메타 라인용 — 메시지가
     * 없는 방은 lastMessage·lastMessageAt이 null이다 (api-contract §3.10).
     */
    public record RoomSummary(Long roomId, Long proposalId, String title, SlotView slot,
            long unreadCount, Status status, String lastMessage,
            LocalDateTime lastMessageAt, long memberCount, int occurrenceCount) {
    }

    /** 스냅샷 슬롯 — fe의 Slot 동형 직렬화는 Task 4 DTO에서 결정한다. */
    public record SlotView(Mode mode, LocalDateTime startTime, LocalDateTime endTime) {
    }

    public record ChatMessageView(Long id, Long senderId, String nickname, String content,
            LocalDateTime createdAt) {
    }

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate txTemplate;

    public ChatService(ChatRoomRepository roomRepository,
            ChatRoomMemberRepository memberRepository, ChatMessageRepository messageRepository,
            UserRepository userRepository, PlatformTransactionManager txManager) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /** scheduling 호출 — proposal당 방 1개(uk_chat_rooms_proposal). 멤버 INSERT는 배치(saveAll). */
    @Transactional
    public ChatRoom createRoom(Long proposalId, String title, Mode mode, LocalDateTime startTime,
            LocalDateTime endTime, List<Long> memberIds, int occurrenceCount) {
        ChatRoom room = roomRepository.save(
                new ChatRoom(proposalId, title, mode, startTime, endTime, occurrenceCount));
        memberRepository.saveAll(memberIds.stream()
                .map(uid -> new ChatRoomMember(room.getId(), uid)).toList());
        return room;
    }

    /**
     * 종결 전이 수신 — 방 없거나 이미 CLOSED면 no-op (멱등).
     * send와 같은 행 잠금(FOR UPDATE)을 공유해 CLOSED 방 송신 교차를 직렬화한다.
     */
    @Transactional
    public void closeRoom(Long proposalId) {
        roomRepository.findByProposalIdForUpdate(proposalId).ifPresent(ChatRoom::close);
    }

    /**
     * 내가 멤버인 전체 방 (OPEN + CLOSED) — CLOSED는 status로 표시, 읽기 전용.
     * unread·lastMessage·memberCount는 각각 방 N개를 GROUP BY 1회로 집계한다 (N+1 방지).
     */
    @Transactional(readOnly = true)
    public List<RoomSummary> myRooms(Long me) {
        List<ChatRoom> rooms = roomRepository.findRoomsByMember(me);
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, Long> unread = memberRepository.countUnreadByRoom(me, roomIds).stream()
                .collect(Collectors.toMap(ChatRoomMemberRepository.UnreadRow::getRoomId,
                        ChatRoomMemberRepository.UnreadRow::getUnread));
        Map<Long, Long> memberCount = memberRepository.countMembersByRoom(roomIds).stream()
                .collect(Collectors.toMap(ChatRoomMemberRepository.CountRow::getRoomId,
                        ChatRoomMemberRepository.CountRow::getCnt));
        List<Long> lastIds = messageRepository.findLastIdByRoomIds(roomIds).stream()
                .map(ChatMessageRepository.LastIdRow::getLastId).toList();
        Map<Long, ChatMessage> lastByRoom = new HashMap<>();
        messageRepository.findAllById(lastIds)
                .forEach(m -> lastByRoom.put(m.getRoomId(), m));
        return rooms.stream()
                .map(room -> {
                    ChatMessage last = lastByRoom.get(room.getId());
                    return new RoomSummary(room.getId(), room.getProposalId(), room.getTitle(),
                            new SlotView(room.getMode(), room.getStartTime(), room.getEndTime()),
                            unread.getOrDefault(room.getId(), 0L), room.getStatus(),
                            last != null ? last.getContent() : null,
                            last != null ? last.getCreatedAt() : null,
                            memberCount.getOrDefault(room.getId(), 0L),
                            room.getOccurrenceCount());
                })
                .toList();
    }

    /**
     * 커서 페이지네이션 — cursor null이면 최신 limit건, 지정 시 id < cursor limit건.
     * DB는 최신순 조회 후 뒤집어 응답은 createdAt 오름차순으로 반환한다 (api-contract §3.10).
     */
    @Transactional(readOnly = true)
    public List<ChatMessageView> history(Long me, Long roomId, Long cursor, Integer limit) {
        // 에러 우선순위 — 방·멤버십(404) 확인이 입력 검증(400)보다 먼저다.
        requireMember(me, roomId);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        List<ChatMessage> page = cursor == null
                ? messageRepository.findByRoomIdOrderByIdDesc(roomId, PageRequest.of(0, size))
                : messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor,
                        PageRequest.of(0, size));
        List<ChatMessage> asc = new ArrayList<>(page);
        // INSERT 전 생성된 createdAt은 동시 송신 시 id 순서와 어긋날 수 있어
        // 계약(createdAt 오름차순)에 맞춰 재정렬한다 — 커서 경계는 id 기준 유지.
        asc.sort(Comparator.comparing(ChatMessage::getCreatedAt)
                .thenComparing(ChatMessage::getId));
        return toViews(asc);
    }

    /**
     * clientMessageId가 있으면 UK(room_id, sender_id, client_message_id) 조회를 먼저 — 있으면 기존 행을
     * 반환해 STOMP 재전송을 멱등 처리한다. null·빈 문자열 키는 매번 새 행.
     * 동시 송신으로 uk_chat_messages_client 충돌이 나면 패자의 트랜잭션은 rollback-only라
     * 같은 트랜잭션 재조회가 불가 — 새 트랜잭션으로 한 번 재시도해 선점자 행을 반환한다.
     */
    public ChatMessageView send(Long me, Long roomId, String content, String clientMessageId) {
        // 길이 검증은 공백 정규화 전 원문 기준 — 64자 초과 공백키 우회 차단
        if (clientMessageId != null && clientMessageId.length() > MAX_CLIENT_MESSAGE_ID) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        try {
            return txTemplate.execute(tx -> sendOnce(me, roomId, content, clientMessageId));
        } catch (DataIntegrityViolationException e) {
            return txTemplate.execute(tx -> sendOnce(me, roomId, content, clientMessageId));
        }
    }

    private ChatMessageView sendOnce(Long me, Long roomId, String content,
            String clientMessageId) {
        requireOpenMemberForUpdate(me, roomId);
        String body = content == null ? "" : content.trim();
        if (body.isEmpty() || body.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        String key = clientMessageId == null || clientMessageId.isBlank() ? null
                : clientMessageId;
        if (key != null) {
            var existing =
                    messageRepository.findByRoomIdAndSenderIdAndClientMessageId(roomId, me, key);
            if (existing.isPresent()) {
                return toViews(List.of(existing.get())).get(0);
            }
        }
        // 방 잠금을 잡은 같은 트랜잭션에서 INSERT — 별도 트랜잭션 분리 시
        // 자식 FK 검사가 부모 행 S락을 요구해 부모 X락과 데드락이 된다
        ChatMessage saved = messageRepository.save(new ChatMessage(roomId, me, body, key));
        return toViews(List.of(saved)).get(0);
    }

    /**
     * 읽음 커서 갱신 — lastReadMessageId는 같은 방의 실존 메시지여야 하고,
     * 현재 커서보다 작은 값은 단조 증가 원칙으로 무시한다 (커서 유지, 멤버 행 반환).
     * 조건부 UPDATE가 DB 단에서 단조성을 보장해 동시 markRead의 마지막 커밋 역행을 막는다.
     */
    @Transactional
    public ChatRoomMember markRead(Long me, Long roomId, Long lastReadMessageId) {
        requireMember(me, roomId);
        if (lastReadMessageId == null
                || !messageRepository.existsByRoomIdAndId(roomId, lastReadMessageId)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        memberRepository.advanceReadCursor(roomId, me, lastReadMessageId);
        return memberRepository.findByRoomIdAndUserId(roomId, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_MEMBER));
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long roomId, Long userId) {
        return memberRepository.existsByRoomIdAndUserId(roomId, userId);
    }

    /** 읽기 경로(이력·읽음 커서)용 — 멤버십만 검사하고 CLOSED 방도 허용한다. */
    private ChatRoomMember requireMember(Long me, Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        return memberRepository.findByRoomIdAndUserId(roomId, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_MEMBER));
    }

    /** send·closeRoom 직렬화용 — 방 행을 FOR UPDATE로 잡고 상태를 확인한다. */
    private ChatRoomMember requireOpenMemberForUpdate(Long me, Long roomId) {
        ChatRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        ChatRoomMember member = memberRepository.findByRoomIdAndUserId(roomId, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_MEMBER));
        if (room.getStatus() == Status.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
        return member;
    }

    private List<ChatMessageView> toViews(List<ChatMessage> messages) {
        Map<Long, String> nicknames = new HashMap<>();
        userRepository.findAllById(
                messages.stream().map(ChatMessage::getSenderId).distinct().toList())
                .forEach(u -> nicknames.put(u.getId(), u.getNickname()));
        return messages.stream()
                .map(m -> new ChatMessageView(m.getId(), m.getSenderId(),
                        nicknames.get(m.getSenderId()), m.getContent(), m.getCreatedAt()))
                .toList();
    }
}
