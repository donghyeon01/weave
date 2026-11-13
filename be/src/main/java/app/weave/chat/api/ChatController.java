package app.weave.chat.api;

import app.weave.chat.domain.ChatRoom.Status;
import app.weave.chat.domain.ChatRoomMember;
import app.weave.chat.domain.Mode;
import app.weave.chat.service.ChatService;
import app.weave.chat.service.ChatService.ChatMessageView;
import app.weave.chat.service.ChatService.SlotView;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** api-contract §3.10 REST 경계 — 방 목록·이력·읽음 커서. */
@RestController
@RequestMapping("/api/chat/rooms")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoomResponse> rooms(@AuthenticationPrincipal Long me) {
        return service.myRooms(me).stream()
                .map(s -> new RoomResponse(s.roomId(), s.proposalId(), s.title(),
                        toRoomSlot(s.slot()), s.unreadCount(), s.status(), s.lastMessage(),
                        s.lastMessageAt(), s.memberCount(), s.occurrenceCount()))
                .toList();
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageView> messages(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return service.history(me, id, cursor, limit);
    }

    @PostMapping("/{id}/read")
    public ReadResponse read(@AuthenticationPrincipal Long me, @PathVariable Long id,
            @Valid @RequestBody ReadRequest req) {
        ChatRoomMember member = service.markRead(me, id, req.lastReadMessageId());
        return new ReadResponse(member.getLastReadMessageId());
    }

    /**
     * 저장된 DATE 슬롯의 end는 배타 상한(+1일 자정 — scheduling ProposalService.toRange 규약)이라
     * 계약상 포괄 endDate로 되돌릴 때 -1일 역변환한다.
     */
    private RoomSlot toRoomSlot(SlotView slot) {
        if (slot.mode() == Mode.TIME) {
            return new RoomSlot.Time(slot.startTime().toLocalDate(),
                    slot.startTime().toLocalTime(), slot.endTime().toLocalTime());
        }
        return new RoomSlot.Days(slot.startTime().toLocalDate(),
                slot.endTime().toLocalDate().minusDays(1));
    }

    /** api-contract §3.8 Slot 동형 — scheduling 모듈 경계상 자체 record로 둔다. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = RoomSlot.Time.class, name = "TIME"),
        @JsonSubTypes.Type(value = RoomSlot.Days.class, name = "DATE")
    })
    public sealed interface RoomSlot {

        record Time(LocalDate date,
                @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                @JsonFormat(pattern = "HH:mm") LocalTime endTime) implements RoomSlot {
        }

        record Days(LocalDate startDate, LocalDate endDate) implements RoomSlot {
        }
    }

    public record RoomResponse(Long roomId, Long proposalId, String title, RoomSlot slot,
            long unreadCount, Status status, String lastMessage, LocalDateTime lastMessageAt,
            long memberCount, int occurrenceCount) {
    }

    public record ReadRequest(@NotNull Long lastReadMessageId) {
    }

    public record ReadResponse(Long lastReadMessageId) {
    }
}
