package app.weave.share.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.DiaryAccess;
import app.weave.common.port.EventAccess;
import app.weave.common.port.SharedDiary;
import app.weave.common.port.SharedEvent;
import app.weave.common.port.SharedPhotoBytes;
import app.weave.friend.service.FriendGroupService;
import app.weave.friend.service.FriendService;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.domain.ShareReplyRepository;
import app.weave.share.domain.ShareRepository;
import app.weave.share.domain.ShareVisibility;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일기·일정 공개 설정 — "누구에게 보내는" 게 아니라 항목의 공개 범위를 열어두는 모델.
 * 항목당 shares 1행, PUT upsert가 유일한 쓰기 경로다. 열람 판정(canView)은 FeedService에
 * 위임하고, 대상 행이라는 개념이 없어 지연 정리도 없다 — 친구 해제·복원은 읽기 시점
 * friendship 검증에 자동으로 따른다.
 * share는 diary·event 엔터티를 모른다 — 항목 조회는 common.port만 경유한다.
 */
@Service
public class ShareService {

    private static final int MAX_LIMIT = 50;

    /** 내 공개 설정 목록 항목 — api-contract §3.11 ShareSummary. */
    public record ShareView(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, ShareVisibility visibility, Long groupId,
            LocalDateTime createdAt, long replyCount) {
    }

    /** 친구 피드 항목 — api-contract §3.11 FeedItem. itemPreview는 카드 발췌 라인. */
    public record FeedItem(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, UserRef owner, LocalDateTime createdAt, long replyCount,
            String itemPreview) {
    }

    /** 상세 — item은 SharedDiary|SharedEvent 원본 레코드 (DTO 매핑은 컨트롤러). */
    public record ShareDetail(Long shareId, ShareItemType itemType, Long itemId, Object item,
            UserRef owner, ShareVisibility visibility, Long groupId, LocalDateTime createdAt,
            long replyCount) {
    }

    private final ShareRepository shareRepository;
    private final ShareReplyRepository replyRepository;
    private final DiaryAccess diaryAccess;
    private final EventAccess eventAccess;
    private final FriendService friendService;
    private final FriendGroupService groupService;
    private final FeedService feedService;
    private final UserRepository userRepository;

    public ShareService(ShareRepository shareRepository, ShareReplyRepository replyRepository,
            DiaryAccess diaryAccess, EventAccess eventAccess, FriendService friendService,
            FriendGroupService groupService, FeedService feedService,
            UserRepository userRepository) {
        this.shareRepository = shareRepository;
        this.replyRepository = replyRepository;
        this.diaryAccess = diaryAccess;
        this.eventAccess = eventAccess;
        this.friendService = friendService;
        this.groupService = groupService;
        this.feedService = feedService;
        this.userRepository = userRepository;
    }

    /**
     * 공개 범위 upsert — 유일한 쓰기 경로. 항목은 본인 소유만(미존재·타인 소유 통합 404).
     * GROUP이면 groupId 필수·본인 그룹만, 나머지 범위에 groupId가 오면 VISIBILITY-INVALID.
     * 저장 후 "이번 설정으로 새로 보이게 된" 구독자에게만 SHARED_ITEM을 발행한다.
     */
    @Transactional
    public Share upsert(Long me, ShareItemType type, Long itemId, ShareVisibility visibility,
            Long groupId) {
        // title은 Diary에서 nullable — map 체인으로 빼면 null title이 "항목 부재"로 오인된다
        String itemTitle = titleOf(itemOf(me, type, itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND)));

        if (visibility == ShareVisibility.GROUP) {
            if (groupId == null || !groupService.ownsGroup(me, groupId)) {
                throw new BusinessException(ErrorCode.VISIBILITY_INVALID);
            }
        } else if (groupId != null) {
            throw new BusinessException(ErrorCode.VISIBILITY_INVALID);
        }

        Optional<Share> existing =
                shareRepository.findByOwnerIdAndItemTypeAndItemId(me, type, itemId);
        // "새로 보이게 된 구독자" 판정의 before — 기존 설정 상태에서 보이던 구독자
        Set<Long> previouslyVisible = existing
                .map(s -> feedService.visibleSubscribers(me, s.getVisibility(), s.getGroupId()))
                .orElse(Set.of());

        Share saved = existing
                .map(s -> {
                    s.updateVisibility(visibility, groupId);
                    return s;
                })
                .orElseGet(() -> {
                    try {
                        // saveAndFlush — UK 충돌을 커밋이 아닌 여기서 잡아 재시도하기 위함
                        return shareRepository.saveAndFlush(
                                new Share(me, type, itemId, visibility, groupId));
                    } catch (DataIntegrityViolationException e) {
                        // 동시 PUT 경합 — uk_shares_item에 진 자는 승자 행을 재조회해 갱신으로 수렴 (재시도 1회)
                        Share raced = shareRepository
                                .findByOwnerIdAndItemTypeAndItemId(me, type, itemId)
                                .orElseThrow(() -> e);
                        raced.updateVisibility(visibility, groupId);
                        return raced;
                    }
                });

        feedService.notifyNewlyVisible(saved, itemTitle, previouslyVisible);
        return saved;
    }

    /** 내 공개 설정 목록 — 관리용이라 비공개 전환(=열어둠 해제) 항목은 제외한다. 원본 삭제도 비노출. */
    @Transactional(readOnly = true)
    public List<ShareView> mine(Long me) {
        List<Share> shares = shareRepository.findByOwnerIdOrderByIdDesc(me).stream()
                .filter(s -> s.getVisibility() != ShareVisibility.PRIVATE).toList();
        Map<Long, Long> replyCounts = replyCountsOf(
                shares.stream().map(Share::getId).toList());
        List<ShareView> result = new ArrayList<>();
        for (Share share : shares) {
            Optional<Object> item = itemOf(share.getOwnerId(), share.getItemType(),
                    share.getItemId());
            if (item.isEmpty()) {
                continue; // 원본 삭제 — 비노출
            }
            result.add(new ShareView(share.getId(), share.getItemType(), share.getItemId(),
                    titleOf(item.get()), share.getVisibility(), share.getGroupId(),
                    share.getCreatedAt(), replyCounts.getOrDefault(share.getId(), 0L)));
        }
        return result;
    }

    /**
     * 친구 피드 — 내 친구들의 "나에게 보이는" 공개 항목 통합 타임라인.
     * share.id desc, id < cursor 커서. 후보는 비PRIVATE만 긁고 canView로 재검증한다
     * (GROUP 멤버십·해제된 친구는 여기서 걸린다). 원본 삭제 항목은 제외.
     */
    @Transactional(readOnly = true)
    public List<FeedItem> feed(Long me, Long cursor, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        Set<Long> friendIds = friendService.acceptedFriendIds(me);
        if (friendIds.isEmpty()) {
            return List.of();
        }
        // 후보를 limit×4 배치로 긁어 필터 — 못 채우면 다음 배치(더 오래된 id)를 이어 받아
        // limit까지 채우거나 후보 소진 시 끝낸다. 커서는 반환된 마지막 share.id 기준 id < cursor.
        List<FeedItem> result = new ArrayList<>();
        long candidateCursor = cursor == null ? Long.MAX_VALUE : cursor;
        while (result.size() < limit) {
            List<Share> candidates = shareRepository
                    .findByOwnerIdInAndVisibilityNotAndIdLessThanOrderByIdDesc(friendIds,
                            ShareVisibility.PRIVATE, candidateCursor,
                            PageRequest.of(0, limit * 4));
            if (candidates.isEmpty()) {
                break;
            }
            candidateCursor = candidates.get(candidates.size() - 1).getId();
            Map<Long, Long> replyCounts = replyCountsOf(
                    candidates.stream().map(Share::getId).toList());
            Map<Long, User> owners = userRepository.findAllById(
                    candidates.stream().map(Share::getOwnerId).distinct().toList())
                    .stream().collect(Collectors.toMap(User::getId, Function.identity()));
            for (Share share : candidates) {
                if (result.size() >= limit) {
                    break;
                }
                if (!feedService.canView(me, share, friendIds)) {
                    continue;
                }
                Optional<Object> item = itemOf(share.getOwnerId(), share.getItemType(),
                        share.getItemId());
                if (item.isEmpty()) {
                    continue; // 원본 삭제 — 비노출
                }
                User owner = owners.get(share.getOwnerId());
                result.add(new FeedItem(share.getId(), share.getItemType(), share.getItemId(),
                        titleOf(item.get()),
                        new UserRef(share.getOwnerId(),
                                owner != null ? owner.getNickname() : null),
                        share.getCreatedAt(), replyCounts.getOrDefault(share.getId(), 0L),
                        previewOf(item.get())));
            }
        }
        return result;
    }

    /** 친구 프로필용 공개 항목 — 비친구는 존재 비노출 404. 보이는 것만 canView로 거른다. */
    @Transactional(readOnly = true)
    public List<FeedItem> byOwner(Long me, Long userId) {
        // 본인 항목은 /mine 경유 — 여기선 isFriend(me,me)=false라 의도적으로 404다
        if (!friendService.isFriend(me, userId)) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        List<Share> shares = shareRepository.findByOwnerIdOrderByIdDesc(userId);
        Map<Long, Long> replyCounts = replyCountsOf(
                shares.stream().map(Share::getId).toList());
        UserRef owner = ownerRef(userId);
        List<FeedItem> result = new ArrayList<>();
        for (Share share : shares) {
            if (!feedService.canView(me, share)) {
                continue;
            }
            Optional<Object> item = itemOf(share.getOwnerId(), share.getItemType(),
                    share.getItemId());
            if (item.isEmpty()) {
                continue;
            }
            result.add(new FeedItem(share.getId(), share.getItemType(), share.getItemId(),
                    titleOf(item.get()), owner, share.getCreatedAt(),
                    replyCounts.getOrDefault(share.getId(), 0L), previewOf(item.get())));
        }
        return result;
    }

    /** 상세 — 소유자·canView 통과자만. 원본 삭제 시 404 (SHARE-ITEM-NOT-FOUND). */
    @Transactional(readOnly = true)
    public ShareDetail detail(Long me, Long shareId) {
        Share share = requireViewer(me, shareId);
        Object item = itemOf(share.getOwnerId(), share.getItemType(), share.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND));
        return new ShareDetail(share.getId(), share.getItemType(), share.getItemId(), item,
                ownerRef(share.getOwnerId()), share.getVisibility(), share.getGroupId(),
                share.getCreatedAt(),
                replyRepository.countByShareIdAndDeletedAtIsNull(shareId));
    }

    /** 공유 일기 사진 — 열람자만, DIARY 공유만. 사진 미존재·EVENT 공유는 통합 404. */
    @Transactional(readOnly = true)
    public SharedPhotoBytes photo(Long me, Long shareId, Long photoId) {
        Share share = requireViewer(me, shareId);
        if (share.getItemType() != ShareItemType.DIARY) {
            throw new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND);
        }
        return diaryAccess.photoBytesOf(share.getOwnerId(), share.getItemId(), photoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND));
    }

    /**
     * 열람자 검증 — ReplyService·photo·detail 공용.
     * 소유자 통과 / canView 통과 / 그 외는 행 없음·비열람 통합 SHARE-NOT-FOUND.
     */
    @Transactional(readOnly = true)
    public Share requireViewer(Long me, Long shareId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (feedService.canView(me, share)) {
            return share;
        }
        throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
    }

    /** owner 기준 포트 조회 — 미존재·타인 소유는 empty (share 입장에선 항목 부재와 동일). */
    private Optional<Object> itemOf(Long ownerId, ShareItemType type, Long itemId) {
        return switch (type) {
            case DIARY -> diaryAccess.diaryOf(ownerId, itemId).map(d -> (Object) d);
            case EVENT -> eventAccess.eventDetailOf(ownerId, itemId).map(e -> (Object) e);
        };
    }

    private String titleOf(Object item) {
        if (item instanceof SharedDiary d) {
            return d.title();
        }
        return ((SharedEvent) item).title();
    }

    private static final int PREVIEW_MAX = 140;

    /**
     * 피드 카드 발췌 — 일기는 본문 앞부분, 일정은 "M월 d일 (요일) HH:mm–HH:mm · 장소 — 설명"
     * 형태로 서버에서 조합한다 (시안 195:597 발췌 라인).
     */
    private String previewOf(Object item) {
        if (item instanceof SharedDiary d) {
            return truncate(d.content());
        }
        SharedEvent e = (SharedEvent) item;
        StringBuilder sb = new StringBuilder(scheduleLine(e));
        if (e.location() != null && !e.location().isBlank()) {
            sb.append(" · ").append(e.location());
        }
        if (e.description() != null && !e.description().isBlank()) {
            sb.append(" — ").append(truncate(e.description()));
        }
        return sb.toString();
    }

    private String scheduleLine(SharedEvent e) {
        var start = e.startTime();
        var end = e.endTime();
        String base = start.getMonthValue() + "월 " + start.getDayOfMonth() + "일 ("
                + "월화수목금토일".charAt(start.getDayOfWeek().getValue() - 1) + ")";
        if (e.allDay()) {
            return start.toLocalDate().equals(end.toLocalDate())
                    ? base + " 종일"
                    : base + " 종일 ~ " + end.getMonthValue() + "월 " + end.getDayOfMonth() + "일";
        }
        return base + " " + String.format("%02d:%02d", start.getHour(), start.getMinute())
                + "–" + String.format("%02d:%02d", end.getHour(), end.getMinute());
    }

    private String truncate(String text) {
        if (text == null || text.length() <= PREVIEW_MAX) {
            return text;
        }
        return text.substring(0, PREVIEW_MAX) + "…";
    }

    /** 공유 N개의 replyCount를 GROUP BY 1쿼리로 집계 — 목록 경로의 N+1 방지. */
    private Map<Long, Long> replyCountsOf(List<Long> shareIds) {
        if (shareIds.isEmpty()) {
            return Map.of();
        }
        return replyRepository.countByShareIds(shareIds).stream()
                .collect(Collectors.toMap(ShareReplyRepository.ReplyCountRow::getShareId,
                        ShareReplyRepository.ReplyCountRow::getCnt));
    }

    private UserRef ownerRef(Long ownerId) {
        String nickname = userRepository.findById(ownerId)
                .map(User::getNickname).orElse(null);
        return new UserRef(ownerId, nickname);
    }
}
