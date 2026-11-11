package app.weave.friend.api;

import app.weave.common.port.EventRef;
import app.weave.friend.service.FriendGroupService;
import app.weave.friend.service.FriendService;
import app.weave.friend.service.FriendService.FriendView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService service;
    private final FriendGroupService groupService;

    public FriendController(FriendService service, FriendGroupService groupService) {
        this.service = service;
        this.groupService = groupService;
    }

    @GetMapping
    public List<FriendshipResponse> list(@AuthenticationPrincipal Long me) {
        return service.listFriends(me).stream().map(this::toResponse).toList();
    }

    @GetMapping("/requests/received")
    public List<FriendshipResponse> received(@AuthenticationPrincipal Long me) {
        return service.listReceived(me).stream().map(this::toResponse).toList();
    }

    @GetMapping("/requests/sent")
    public List<FriendshipResponse> sent(@AuthenticationPrincipal Long me) {
        return service.listSent(me).stream().map(this::toResponse).toList();
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendshipResponse> request(@AuthenticationPrincipal Long me,
            @Valid @RequestBody SendRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(service.request(me, req.email(), req.message())));
    }

    @PostMapping("/requests/{id}/accept")
    public FriendshipResponse accept(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        return toResponse(service.accept(me, id));
    }

    /** 친구 일정 미리보기 — 시간 점유 블록만, 메타는 어떤 필드도 노출하지 않는다 (계약 §3.6). */
    @GetMapping("/{id}/schedule")
    public FriendScheduleResponse schedule(@AuthenticationPrincipal Long me,
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<BlockSummary> blocks = service.schedule(me, id, start, end).stream()
                .map(b -> new BlockSummary(b.startTime().toString(), b.endTime().toString()))
                .toList();
        return new FriendScheduleResponse(blocks);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    /** 피드 알람 설정 — 친구만 가능 (비친구 400). 여기서 {userId}는 상대 사용자 id다. */
    @PutMapping("/{userId}/feed-subscription")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal Long me,
            @PathVariable Long userId) {
        groupService.subscribe(me, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/feed-subscription")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Long me,
            @PathVariable Long userId) {
        groupService.unsubscribe(me, userId);
        return ResponseEntity.noContent().build();
    }

    /** 내 피드 알림 구독 목록 — 구독 중인 친구의 user_id 배열 (토글 초기 상태용). */
    @GetMapping("/feed-subscriptions")
    public List<Long> feedSubscriptions(@AuthenticationPrincipal Long me) {
        return groupService.subscribedIds(me);
    }

    private FriendshipResponse toResponse(FriendView view) {
        var u = view.counterparty();
        EventRef next = view.nextEvent();
        return new FriendshipResponse(view.friendship().getId(),
                view.friendship().getStatus().name(),
                new UserSummary(u.getId(), u.getNickname(), u.getEmail(), u.getProfileImage()),
                view.friendship().getMessage(),
                next == null ? null
                        : new NextEvent(next.id(), next.title(), next.startTime().toString(),
                                next.endTime().toString()),
                view.friendship().getCreatedAt().toString());
    }

    public record SendRequest(@NotBlank String email, @Size(max = 100) String message) {
    }

    public record UserSummary(Long id, String nickname, String email, String profileImage) {
    }

    public record NextEvent(Long id, String title, String startTime, String endTime) {
    }

    public record BlockSummary(String startTime, String endTime) {
    }

    public record FriendScheduleResponse(List<BlockSummary> blocks) {
    }

    public record FriendshipResponse(Long friendshipId, String status, UserSummary user,
            String message, NextEvent nextEvent, String createdAt) {
    }
}
