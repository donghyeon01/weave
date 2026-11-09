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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제안 범위 단체채팅 — scheduling이 방 생성·종료를 호출하는 단방향 계약의 수신 측.
 * chat은 scheduling을 모른다 (proposal_id는 논리 참조, 슬롯·멤버는 스냅샷).
 * 에러 우선순위: 방 미존재(404) → 비멤버(404, 소유권 규약상 403 아님) → CLOSED(409) → 입력 검증.
 */
@Service
public class ChatService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_CONTENT = 2000;
    private static final int MAX_CLIENT_MESSAGE_ID = 64;

    /** 방 목록 항목 — slot은 생성 시 스냅샷 그대로. */
    public record RoomSummary(Long roomId, Long proposalId, String title, SlotView slot,
            long unreadCount) {
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

    public ChatService(ChatRoomRepository roomRepository,
            ChatRoomMemberRepository memberRepository, ChatMessageRepository messageRepository,
            UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /** scheduling 호출 — proposal당 방 1개(uk_chat_rooms_proposal). 멤버 INSERT는 배치(saveAll). */
    @Transactional
    public ChatRoom createRoom(Long proposalId, String title, Mode mode, LocalDateTime startTime,
            LocalDateTime endTime, List<Long> memberIds) {
        ChatRoom room = roomRepository.save(
                new ChatRoom(proposalId, title, mode, startTime, endTime));
        memberRepository.saveAll(memberIds.stream()
                .map(uid -> new ChatRoomMember(room.getId(), uid)).toList());
        return room;
    }

    /** 종결 전이 수신 — 방 없거나 이미 CLOSED면 no-op (멱등). */
    @Transactional
    public void closeRoom(Long proposalId) {
        roomRepository.findByProposalId(proposalId).ifPresent(ChatRoom::close);
    }

    @Transactional(readOnly = true)
    public List<RoomSummary> openRooms(Long me) {
        Map<Long, ChatRoomMember> myMembers = memberRepository.findByUserId(me).stream()
                .collect(Collectors.toMap(ChatRoomMember::getRoomId, Function.identity()));
        return roomRepository.findOpenRoomsByMember(me).stream()
                .map(room -> {
                    Long cursor = myMembers.get(room.getId()).getLastReadMessageId();
                    long unread = messageRepository.countByRoomIdAndIdGreaterThanAndSenderIdNot(
                            room.getId(), cursor == null ? 0L : cursor, me);
                    return new RoomSummary(room.getId(), room.getProposalId(), room.getTitle(),
                            new SlotView(room.getMode(), room.getStartTime(), room.getEndTime()),
                            unread);
                }).toList();
    }

    /**
     * 커서 페이지네이션 — cursor null이면 최신 limit건, 지정 시 id < cursor limit건.
     * DB는 최신순 조회 후 뒤집어 응답은 createdAt 오름차순으로 반환한다 (api-contract §3.10).
     */
    @Transactional(readOnly = true)
    public List<ChatMessageView> history(Long me, Long roomId, Long cursor, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        requireOpenMember(me, roomId);
        List<ChatMessage> page = cursor == null
                ? messageRepository.findByRoomIdOrderByIdDesc(roomId, PageRequest.of(0, size))
                : messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor,
                        PageRequest.of(0, size));
        List<ChatMessage> asc = new ArrayList<>(page);
        Collections.reverse(asc);
        return toViews(asc);
    }

    /**
     * clientMessageId가 있으면 UK(room_id, client_message_id) 조회를 먼저 — 있으면 기존 행을
     * 반환해 STOMP 재전송을 멱등 처리한다. null·빈 문자열 키는 매번 새 행.
     */
    @Transactional
    public ChatMessageView send(Long me, Long roomId, String content, String clientMessageId) {
        requireOpenMember(me, roomId);
        String body = content == null ? "" : content.trim();
        if (body.isEmpty() || body.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        String key = clientMessageId == null || clientMessageId.isBlank() ? null
                : clientMessageId;
        if (key != null && key.length() > MAX_CLIENT_MESSAGE_ID) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        if (key != null) {
            var existing = messageRepository.findByRoomIdAndClientMessageId(roomId, key);
            if (existing.isPresent()) {
                return toViews(List.of(existing.get())).get(0);
            }
        }
        ChatMessage saved = messageRepository.save(new ChatMessage(roomId, me, body, key));
        return toViews(List.of(saved)).get(0);
    }

    /**
     * 읽음 커서 갱신 — lastReadMessageId는 같은 방의 실존 메시지여야 하고,
     * 현재 커서보다 작은 값은 단조 증가 원칙으로 무시한다 (커서 유지, 멤버 행 반환).
     */
    @Transactional
    public ChatRoomMember markRead(Long me, Long roomId, Long lastReadMessageId) {
        ChatRoomMember member = requireOpenMember(me, roomId);
        if (lastReadMessageId == null
                || !messageRepository.existsByRoomIdAndId(roomId, lastReadMessageId)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_INVALID);
        }
        member.markRead(lastReadMessageId);
        return member;
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long roomId, Long userId) {
        return memberRepository.existsByRoomIdAndUserId(roomId, userId);
    }

    private ChatRoomMember requireOpenMember(Long me, Long roomId) {
        ChatRoom room = roomRepository.findById(roomId)
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
