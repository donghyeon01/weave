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
import app.weave.friend.service.FriendService;
import app.weave.notification.service.NotificationPayload;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.notification.service.NotificationPublisher;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.domain.ShareReplyRepository;
import app.weave.share.domain.ShareRepository;
import app.weave.share.domain.ShareTarget;
import app.weave.share.domain.ShareTargetRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일기·일정 공유 — 가시성은 share_targets 행 ∧ 실시간 ACCEPTED 친구 이중 검증.
 * friend→share 역방향 호출이 순환이라 친구 해제 통지 경로가 없으므로, 읽기 경로에서
 * 무효 대상 행을 지연 정리(lazy purge)해 회수를 영속화한다 — 재친구해도 미부활.
 * 지연 정리는 쓰기라 조회 경로도 readOnly가 아닌 트랜잭션으로 연다.
 * share는 diary·event 엔터티를 모른다 — 항목 조회는 common.port만 경유한다.
 */
@Service
public class ShareService {

    private static final int MAX_LIMIT = 50;

    /** 내 공유 목록 항목 — api-contract §3.11 ShareSummary. */
    public record ShareView(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, LocalDateTime createdAt, List<Long> targetUserIds,
            long replyCount) {
    }

    /** 받은 공유 피드 항목 — api-contract §3.11 ReceivedShare. */
    public record ReceivedShare(Long shareId, ShareItemType itemType, Long itemId,
            String itemTitle, UserRef owner, LocalDateTime createdAt, long replyCount) {
    }

    /**
     * 상세 — item은 SharedDiary|SharedEvent 원본 레코드 (DTO 매핑은 컨트롤러).
     * targetUserIds는 소유자에게만 채운다 — 대상 열람자에게는 null.
     */
    public record ShareDetail(Long shareId, ShareItemType itemType, Long itemId, Object item,
            UserRef owner, List<Long> targetUserIds, LocalDateTime createdAt,
            long replyCount) {
    }

    private final ShareRepository shareRepository;
    private final ShareTargetRepository targetRepository;
    private final ShareReplyRepository replyRepository;
    private final DiaryAccess diaryAccess;
    private final EventAccess eventAccess;
    private final FriendService friendService;
    private final NotificationPublisher publisher;
    private final UserRepository userRepository;
    private final TransactionTemplate purgeTx;

    public ShareService(ShareRepository shareRepository, ShareTargetRepository targetRepository,
            ShareReplyRepository replyRepository, DiaryAccess diaryAccess,
            EventAccess eventAccess, FriendService friendService,
            NotificationPublisher publisher, UserRepository userRepository,
            PlatformTransactionManager txManager) {
        this.shareRepository = shareRepository;
        this.targetRepository = targetRepository;
        this.replyRepository = replyRepository;
        this.diaryAccess = diaryAccess;
        this.eventAccess = eventAccess;
        this.friendService = friendService;
        this.publisher = publisher;
        this.userRepository = userRepository;
        // 지연 정리 삭제는 호출 트랜잭션 롤백(BusinessException)과 무관하게
        // 커밋돼야 회수가 영속화된다 — 별도 REQUIRES_NEW 트랜잭션으로 분리
        this.purgeTx = new TransactionTemplate(txManager);
        this.purgeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 공유 생성 — 항목은 본인 소유만(미존재·타인 소유 통합 404), 대상은 전원 ACCEPTED 친구.
     * uk_shares_item 충돌은 409. 저장 후 대상 전원에게 SHARED_ITEM을 발행한다.
     */
    @Transactional
    public Share create(Long me, ShareItemType type, Long itemId, List<Long> targetUserIds) {
        String itemTitle = itemOf(me, type, itemId)
                .map(this::titleOf)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND));

        if (targetUserIds == null || targetUserIds.isEmpty() || targetUserIds.contains(me)) {
            throw new BusinessException(ErrorCode.SHARE_TARGET_INVALID);
        }
        List<Long> targets = new ArrayList<>(new LinkedHashSet<>(targetUserIds));
        if (!friendService.acceptedFriendIds(me).containsAll(targets)) {
            throw new BusinessException(ErrorCode.SHARE_TARGET_INVALID);
        }

        if (shareRepository.existsByOwnerIdAndItemTypeAndItemId(me, type, itemId)) {
            throw new BusinessException(ErrorCode.SHARE_ALREADY_EXISTS);
        }
        Share saved;
        try {
            // 동시 생성 경합의 UK 충돌도 409로 매핑 — saveAndFlush로 이 트랜잭션 안에서 잡는다
            saved = shareRepository.saveAndFlush(new Share(me, type, itemId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SHARE_ALREADY_EXISTS);
        }
        targetRepository.saveAll(
                targets.stream().map(uid -> new ShareTarget(saved.getId(), uid)).toList());

        UserRef owner = ownerRef(me);
        for (Long uid : targets) {
            publisher.publish(uid, new NotificationPayload.SharedItem(saved.getId(),
                    type.name(), itemId, owner, itemTitle == null ? "" : itemTitle));
        }
        return saved;
    }

    /**
     * 내 공유 목록 — 원본 삭제 항목은 제외. 친구 해제로 무효화된 대상 행은 지연 정리한다.
     */
    @Transactional
    public List<ShareView> myShares(Long me) {
        Set<Long> friendIds = friendService.acceptedFriendIds(me);
        List<ShareView> result = new ArrayList<>();
        for (Share share : shareRepository.findByOwnerIdOrderByCreatedAtDesc(me)) {
            List<Long> validTargets = validTargetsOrPurge(share.getId(), friendIds);
            Optional<Object> item = itemOf(share.getOwnerId(), share.getItemType(),
                    share.getItemId());
            if (item.isEmpty()) {
                continue; // 원본 삭제 — 비노출
            }
            result.add(new ShareView(share.getId(), share.getItemType(), share.getItemId(),
                    titleOf(item.get()), share.getCreatedAt(), validTargets,
                    replyRepository.countByShareIdAndDeletedAtIsNull(share.getId())));
        }
        return result;
    }

    /**
     * 받은 공유 피드 — share.id desc, id < cursor 커서. owner가 더 이상 친구가 아닌
     * 대상 행은 지연 정리 후 제외하고, 원본 삭제 항목도 제외한다.
     */
    @Transactional
    public List<ReceivedShare> received(Long me, Long cursor, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        Set<Long> friendIds = friendService.acceptedFriendIds(me);
        List<Long> shareIds = targetRepository.findByUserId(me).stream()
                .map(ShareTarget::getShareId)
                .filter(id -> cursor == null || id < cursor)
                .sorted(Comparator.reverseOrder())
                .toList();
        Map<Long, Share> shares = shareRepository.findAllById(shareIds).stream()
                .collect(Collectors.toMap(Share::getId, Function.identity()));
        Map<Long, User> owners = userRepository.findAllById(
                shares.values().stream().map(Share::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<ReceivedShare> result = new ArrayList<>();
        for (Long shareId : shareIds) {
            if (result.size() >= limit) {
                break;
            }
            Share share = shares.get(shareId);
            if (share == null) {
                continue;
            }
            if (!friendIds.contains(share.getOwnerId())) {
                // 친구 해제 — 대상 행을 삭제해 회수를 영속화하고 피드에서 제외
                targetRepository.deleteByShareIdAndUserId(shareId, me);
                continue;
            }
            Optional<Object> item = itemOf(share.getOwnerId(), share.getItemType(),
                    share.getItemId());
            if (item.isEmpty()) {
                continue; // 원본 삭제 — 비노출
            }
            User owner = owners.get(share.getOwnerId());
            result.add(new ReceivedShare(share.getId(), share.getItemType(),
                    share.getItemId(), titleOf(item.get()),
                    new UserRef(share.getOwnerId(), owner != null ? owner.getNickname() : null),
                    share.getCreatedAt(),
                    replyRepository.countByShareIdAndDeletedAtIsNull(shareId)));
        }
        return result;
    }

    /** 상세 — 소유자·유효 대상만. 원본 삭제 시 404 (SHARE-ITEM-NOT-FOUND). */
    @Transactional
    public ShareDetail detail(Long me, Long shareId) {
        Share share = requireAudience(me, shareId);
        Object item = itemOf(share.getOwnerId(), share.getItemType(), share.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND));
        List<Long> targetUserIds = share.getOwnerId().equals(me)
                ? validTargetsOrPurge(shareId, friendService.acceptedFriendIds(me))
                : null;
        return new ShareDetail(share.getId(), share.getItemType(), share.getItemId(), item,
                ownerRef(share.getOwnerId()), targetUserIds, share.getCreatedAt(),
                replyRepository.countByShareIdAndDeletedAtIsNull(shareId));
    }

    /**
     * 대상 편집 — 소유자만(비소유자는 SHARE-NOT-FOUND). add는 친구 검증 후
     * 신규 대상만 SHARED_ITEM 발행, remove는 행 삭제로 즉시 비가시.
     */
    @Transactional
    public void editTargets(Long me, Long shareId, List<Long> add, List<Long> remove) {
        Share share = shareRepository.findById(shareId)
                .filter(s -> s.getOwnerId().equals(me))
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));

        Set<Long> friendIds = friendService.acceptedFriendIds(me);
        List<Long> current = validTargetsOrPurge(shareId, friendIds);

        if (add != null && !add.isEmpty()) {
            List<Long> requested = new ArrayList<>(new LinkedHashSet<>(add));
            if (requested.contains(me) || !friendIds.containsAll(requested)) {
                throw new BusinessException(ErrorCode.SHARE_TARGET_INVALID);
            }
            List<Long> newTargets = requested.stream()
                    .filter(uid -> !current.contains(uid)).toList();
            if (!newTargets.isEmpty()) {
                targetRepository.saveAll(newTargets.stream()
                        .map(uid -> new ShareTarget(shareId, uid)).toList());
                String itemTitle = itemOf(me, share.getItemType(), share.getItemId())
                        .map(this::titleOf).orElse("");
                UserRef owner = ownerRef(me);
                for (Long uid : newTargets) {
                    publisher.publish(uid, new NotificationPayload.SharedItem(shareId,
                            share.getItemType().name(), share.getItemId(), owner,
                            itemTitle == null ? "" : itemTitle));
                }
            }
        }

        if (remove != null && !remove.isEmpty()) {
            List<Long> removing = remove.stream().filter(current::contains).toList();
            if (!removing.isEmpty()) {
                targetRepository.deleteByShareIdAndUserIdIn(shareId, removing);
            }
        }
    }

    /** 공유 해제 — 소유자만. targets·replies는 FK CASCADE 연쇄 삭제. */
    @Transactional
    public void delete(Long me, Long shareId) {
        Share share = shareRepository.findById(shareId)
                .filter(s -> s.getOwnerId().equals(me))
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        shareRepository.delete(share);
    }

    /** 공유 일기 사진 — 열람자만, DIARY 공유만. 사진 미존재·EVENT 공유는 통합 404. */
    @Transactional
    public SharedPhotoBytes photo(Long me, Long shareId, Long photoId) {
        Share share = requireAudience(me, shareId);
        if (share.getItemType() != ShareItemType.DIARY) {
            throw new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND);
        }
        return diaryAccess.photoBytesOf(share.getOwnerId(), share.getItemId(), photoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_ITEM_NOT_FOUND));
    }

    /**
     * 열람자 검증 — ReplyService·photo·detail 공용.
     * 소유자 통과 / 대상 행 ∧ 현재 친구 통과 / 대상이지만 비친구면 지연 정리 후
     * SHARE-NOT-AUDIENCE / 대상 행 자체가 없으면 SHARE-NOT-AUDIENCE.
     */
    @Transactional
    public Share requireAudience(Long me, Long shareId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (share.getOwnerId().equals(me)) {
            return share;
        }
        Optional<ShareTarget> target = targetRepository.findByShareIdAndUserId(shareId, me);
        if (target.isPresent() && friendService.isFriend(me, share.getOwnerId())) {
            return share;
        }
        if (target.isPresent()) {
            // 친구 해제로 무효화된 대상 행 — 삭제해 회수를 영속화한다 (재친구 미부활).
            // 이어서 던지는 예외가 호출 트랜잭션을 롤백시키므로 삭제는 REQUIRES_NEW로 커밋한다.
            purgeTx.executeWithoutResult(
                    tx -> targetRepository.deleteByShareIdAndUserId(shareId, me));
        }
        throw new BusinessException(ErrorCode.SHARE_NOT_AUDIENCE);
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

    /**
     * 현재 친구인 대상만 남기고 무효 행을 삭제한다 — 소유자 경로(myShares·detail·editTargets)
     * 의 지연 정리. 반환값은 유효 대상 user_id 목록.
     */
    private List<Long> validTargetsOrPurge(Long shareId, Set<Long> friendIds) {
        List<Long> stale = new ArrayList<>();
        List<Long> valid = new ArrayList<>();
        for (ShareTarget t : targetRepository.findByShareId(shareId)) {
            (friendIds.contains(t.getUserId()) ? valid : stale).add(t.getUserId());
        }
        if (!stale.isEmpty()) {
            targetRepository.deleteByShareIdAndUserIdIn(shareId, stale);
        }
        return valid;
    }

    private UserRef ownerRef(Long ownerId) {
        String nickname = userRepository.findById(ownerId)
                .map(User::getNickname).orElse(null);
        return new UserRef(ownerId, nickname);
    }
}
