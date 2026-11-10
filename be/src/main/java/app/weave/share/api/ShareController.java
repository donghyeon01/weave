package app.weave.share.api;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.SharedDiary;
import app.weave.common.port.SharedEvent;
import app.weave.common.port.SharedPhotoBytes;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.domain.ShareReply;
import app.weave.share.domain.ShareVisibility;
import app.weave.share.service.ReplyService;
import app.weave.share.service.ReplyService.ReplyNode;
import app.weave.share.service.ShareService;
import app.weave.share.service.ShareService.FeedItem;
import app.weave.share.service.ShareService.ShareDetail;
import app.weave.share.service.ShareService.ShareView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** api-contract §3.11 REST 경계 — 공개 설정 upsert·내 목록·피드·친구별·상세·답글·사진. */
@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService service;
    private final ReplyService replyService;

    public ShareController(ShareService service, ReplyService replyService) {
        this.service = service;
        this.replyService = replyService;
    }

    /** 공개 범위 upsert — 항목당 1행의 유일한 쓰기 경로. PRIVATE 행도 남긴다(답글 보존). */
    @PutMapping
    public ShareDetailResponse upsert(@AuthenticationPrincipal Long me,
            @Valid @RequestBody UpsertShareRequest req) {
        // visibility는 bean validation을 거치지 않고 여기서 파싱한다 — enum 외 값은
        // 계약상 COMMON-VALIDATION이 아니라 VISIBILITY-INVALID로 매핑해야 한다
        ShareVisibility visibility;
        try {
            visibility = ShareVisibility.valueOf(req.visibility());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VISIBILITY_INVALID);
        }
        Share share = service.upsert(me, ShareItemType.valueOf(req.itemType()), req.itemId(),
                visibility, req.groupId());
        return toDetail(service.detail(me, share.getId()));
    }

    /** 내 공개 설정 목록 — 관리용 (비공개 전환 항목 제외). */
    @GetMapping("/mine")
    public List<ShareSummaryResponse> mine(@AuthenticationPrincipal Long me) {
        return service.mine(me).stream()
                .map(s -> new ShareSummaryResponse(s.shareId(), s.itemType(), s.itemId(),
                        s.itemTitle(), s.visibility(), s.groupId(), s.createdAt(),
                        s.replyCount()))
                .toList();
    }

    /** 친구 피드 — 친구들의 "나에게 보이는" 공개 항목 통합 타임라인 (id desc 커서). */
    @GetMapping("/feed")
    public List<FeedItemResponse> feed(@AuthenticationPrincipal Long me,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.feed(me, cursor, limit).stream()
                .map(s -> new FeedItemResponse(s.shareId(), s.itemType(), s.itemId(),
                        s.itemTitle(), s.owner(), s.createdAt(), s.replyCount(),
                        s.itemPreview()))
                .toList();
    }

    /** 친구 프로필용 — 그 친구의 공개 항목 중 나에게 보이는 것 (비친구 404). */
    @GetMapping("/by/{userId}")
    public List<FeedItemResponse> byOwner(@AuthenticationPrincipal Long me,
            @PathVariable Long userId) {
        return service.byOwner(me, userId).stream()
                .map(s -> new FeedItemResponse(s.shareId(), s.itemType(), s.itemId(),
                        s.itemTitle(), s.owner(), s.createdAt(), s.replyCount(),
                        s.itemPreview()))
                .toList();
    }

    @GetMapping("/{id}")
    public ShareDetailResponse detail(@AuthenticationPrincipal Long me,
            @PathVariable Long id) {
        return toDetail(service.detail(me, id));
    }

    /** 답글 스레드 — 루트만 id > cursor 오름차순, children에 대댓글 임베드 (§3.11). */
    @GetMapping("/{id}/replies")
    public List<ReplyNodeResponse> replies(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return replyService.list(me, id, cursor, limit).stream()
                .map(this::toReplyNode).toList();
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<ReplyNodeResponse> createReply(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @RequestBody ReplyRequest req) {
        ShareReply reply = replyService.create(me, id, req.content(), req.parentId());
        return ResponseEntity.created(
                java.net.URI.create("/api/shares/" + id + "/replies/" + reply.getId()))
                .body(toReplyNode(replyService.nodeOf(reply)));
    }

    @PatchMapping("/{id}/replies/{rid}")
    public ReplyNodeResponse updateReply(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @PathVariable Long rid,
            @RequestBody ReplyRequest req) {
        return toReplyNode(replyService.nodeOf(replyService.update(me, id, rid, req.content())));
    }

    @DeleteMapping("/{id}/replies/{rid}")
    public ResponseEntity<Void> deleteReply(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @PathVariable Long rid) {
        replyService.delete(me, id, rid);
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

    /** ReplyNode → 계약 형태 — author는 {"id","nickname"} 객체로 감싼다. */
    private ReplyNodeResponse toReplyNode(ReplyNode n) {
        return new ReplyNodeResponse(n.id(), new UserRef(n.authorId(), n.nickname()),
                n.content(), n.createdAt(), n.updatedAt(), n.deleted(),
                n.children().stream().map(this::toReplyNode).toList());
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
                d.visibility(), d.groupId(), d.createdAt(), d.replyCount());
    }

    /** groupId는 visibility=GROUP일 때만 필수 — 검증은 서비스(VISIBILITY-INVALID). */
    public record UpsertShareRequest(
            @NotNull @Pattern(regexp = "DIARY|EVENT") String itemType,
            @NotNull Long itemId,
            @NotNull String visibility,
            Long groupId) {
    }

    /** parentId는 PATCH에선 무시된다 — 생성·수정 요청을 하나의 record로 받는다. */
    public record ReplyRequest(String content, Long parentId) {
    }

    /** 계약 §3.11 ReplyNode — deleted=true면 content는 "" (tombstone). */
    public record ReplyNodeResponse(Long id, UserRef author, String content,
            LocalDateTime createdAt, LocalDateTime updatedAt, boolean deleted,
            List<ReplyNodeResponse> children) {
    }

    public record ShareSummaryResponse(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, ShareVisibility visibility, Long groupId,
            LocalDateTime createdAt, long replyCount) {
    }

    /** itemPreview — 일기는 본문 발췌, 일정은 일시·장소·설명 조합 (시안 피드 카드 발췌 라인). */
    public record FeedItemResponse(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, UserRef owner, LocalDateTime createdAt, long replyCount,
            String itemPreview) {
    }

    /** item은 itemType별 DiaryItem|EventItem — 계약 §3.11 임베드 규칙. */
    public record ShareDetailResponse(Long shareId, ShareItemType itemType, Long itemId,
            Object item, UserRef owner, ShareVisibility visibility, Long groupId,
            LocalDateTime createdAt, long replyCount) {
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
