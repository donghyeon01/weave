package app.weave.share.api;

import app.weave.common.port.SharedDiary;
import app.weave.common.port.SharedEvent;
import app.weave.common.port.SharedPhotoBytes;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.service.ShareService;
import app.weave.share.service.ShareService.ReceivedShare;
import app.weave.share.service.ShareService.ShareDetail;
import app.weave.share.service.ShareService.ShareView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** api-contract §3.11 REST 경계 — 공유 생성·목록·피드·상세·대상 편집·해제·사진. */
@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService service;

    public ShareController(ShareService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShareDetailResponse> create(@AuthenticationPrincipal Long me,
            @Valid @RequestBody CreateShareRequest req) {
        Share share = service.create(me, ShareItemType.valueOf(req.itemType()), req.itemId(),
                req.targetUserIds());
        return ResponseEntity.created(URI.create("/api/shares/" + share.getId()))
                .body(toDetail(service.detail(me, share.getId())));
    }

    @GetMapping
    public List<ShareSummaryResponse> mine(@AuthenticationPrincipal Long me) {
        return service.myShares(me).stream()
                .map(s -> new ShareSummaryResponse(s.shareId(), s.itemType(), s.itemId(),
                        s.itemTitle(), s.createdAt(), s.targetUserIds(), s.replyCount()))
                .toList();
    }

    @GetMapping("/received")
    public List<ReceivedShareResponse> received(@AuthenticationPrincipal Long me,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.received(me, cursor, limit).stream()
                .map(s -> new ReceivedShareResponse(s.shareId(), s.itemType(), s.itemId(),
                        s.itemTitle(), s.owner(), s.createdAt(), s.replyCount()))
                .toList();
    }

    @GetMapping("/{id}")
    public ShareDetailResponse detail(@AuthenticationPrincipal Long me,
            @PathVariable Long id) {
        return toDetail(service.detail(me, id));
    }

    @PatchMapping("/{id}/targets")
    public ShareDetailResponse editTargets(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @RequestBody EditTargetsRequest req) {
        service.editTargets(me, id, req.add(), req.remove());
        return toDetail(service.detail(me, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long me,
            @PathVariable Long id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    /** 사진 서빙 — <img>가 Authorization을 못 보내므로 fe는 인증 fetch→objectURL로 표시한다. */
    @GetMapping("/{id}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @PathVariable Long photoId) {
        SharedPhotoBytes photo = service.photo(me, id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .body(photo.bytes());
    }

    private ShareDetailResponse toDetail(ShareDetail d) {
        Object item = switch (d.itemType()) {
            case DIARY -> {
                SharedDiary diary = (SharedDiary) d.item();
                yield new DiaryItem(diary.date(), diary.title(), diary.content(),
                        diary.mood() == null ? null : diary.mood().toLowerCase(),
                        diary.tags(),
                        diary.photos().stream()
                                .map(p -> new PhotoItem(p.id(), p.contentType(), p.sortOrder()))
                                .toList());
            }
            case EVENT -> {
                SharedEvent event = (SharedEvent) d.item();
                yield new EventItem(event.title(), event.description(), event.startTime(),
                        event.endTime(), event.location(), event.allDay());
            }
        };
        return new ShareDetailResponse(d.shareId(), d.itemType(), d.itemId(), item, d.owner(),
                d.targetUserIds(), d.createdAt(), d.replyCount());
    }

    public record CreateShareRequest(
            @NotNull @Pattern(regexp = "DIARY|EVENT") String itemType,
            @NotNull Long itemId,
            List<Long> targetUserIds) {
    }

    public record EditTargetsRequest(List<Long> add, List<Long> remove) {
    }

    public record ShareSummaryResponse(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, LocalDateTime createdAt, List<Long> targetUserIds,
            long replyCount) {
    }

    public record ReceivedShareResponse(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, UserRef owner, LocalDateTime createdAt, long replyCount) {
    }

    /** item은 itemType별 DiaryItem|EventItem — 계약 §3.11 임베드 규칙. */
    public record ShareDetailResponse(Long shareId, ShareItemType itemType, Long itemId,
            Object item, UserRef owner, List<Long> targetUserIds, LocalDateTime createdAt,
            long replyCount) {
    }

    public record DiaryItem(LocalDate date, String title, String content, String mood,
            List<String> tags, List<PhotoItem> photos) {
    }

    public record PhotoItem(Long id, String contentType, Integer sortOrder) {
    }

    /** 계약상 participants 미포함 — SharedEvent에도 해당 필드가 없다. */
    public record EventItem(String title, String description, LocalDateTime startTime,
            LocalDateTime endTime, String location, boolean allDay) {
    }
}
