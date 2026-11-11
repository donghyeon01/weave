package app.weave.event.api;

import app.weave.auth.domain.User;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.event.domain.Event;
import app.weave.event.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Event e = service.create(userId, req.title(), req.description(), req.startTime(),
                req.endTime(), req.location(), Boolean.TRUE.equals(req.allDay()),
                parseBusyPolicy(req.busyPolicy()), idempotencyKey);
        return ResponseEntity.created(URI.create("/api/events/" + e.getId()))
                .body(toResponse(userId, e));
    }

    @GetMapping
    public List<EventResponse> list(@AuthenticationPrincipal Long userId) {
        List<Event> events = service.listAll(userId);
        return toResponses(userId, events);
    }

    /** start·end는 포함 끝 날짜 — end 당일 일정도 포함 (계약 §3.4). */
    @GetMapping("/range")
    public List<EventResponse> range(@AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return toResponses(userId, service.findRange(userId, start, end));
    }

    @PatchMapping("/{id}")
    public EventResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestBody UpdateRequest req) {
        return toResponse(userId, service.update(userId, id, req.title(), req.description(),
                req.startTime(), req.endTime(), req.location(), req.allDay(),
                parseBusyPolicy(req.busyPolicy())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private List<EventResponse> toResponses(Long me, List<Event> events) {
        Map<Long, List<User>> participants = service.participantsOf(me, events);
        return events.stream().map(e -> toResponse(e, participants.get(e.getId()))).toList();
    }

    /** 단일 응답도 participants 실데이터 — mutation도 같은 계약 (§3.4). */
    private EventResponse toResponse(Long me, Event e) {
        return toResponse(e, service.participantsOf(me, List.of(e)).get(e.getId()));
    }

    private EventResponse toResponse(Event e, List<User> participants) {
        List<ParticipantSummary> summaries = participants == null ? List.of()
                : participants.stream()
                        .map(u -> new ParticipantSummary(u.getId(), u.getNickname(),
                                u.getProfileImage()))
                        .toList();
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(),
                e.getStartTime().toString(), e.getEndTime().toString(), e.getLocation(),
                e.isAllDay(), e.getSource().name().toLowerCase(),
                e.getBusyPolicy().name(), summaries);
    }

    /** 계약 enum은 대문자 — 소문자 입력도 관대하게 받되, 미지정 값은 COMMON-VALIDATION(400). */
    private Event.BusyPolicy parseBusyPolicy(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Event.BusyPolicy.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
    }

    public record CreateRequest(@NotBlank @Size(max = 255) String title,
            @Size(max = 500) String description, @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime, @Size(max = 255) String location, Boolean allDay,
            String busyPolicy) {
    }

    public record UpdateRequest(@Size(max = 255) String title, @Size(max = 500) String description,
            LocalDateTime startTime, LocalDateTime endTime, @Size(max = 255) String location,
            Boolean allDay, String busyPolicy) {
    }

    public record ParticipantSummary(Long id, String nickname, String profileImage) {
    }

    public record EventResponse(Long id, String title, String description, String startTime,
            String endTime, String location, boolean allDay, String source,
            String busyPolicy, List<ParticipantSummary> participants) {
    }
}
