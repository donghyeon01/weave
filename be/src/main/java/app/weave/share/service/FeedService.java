package app.weave.share.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.friend.service.FriendGroupService;
import app.weave.friend.service.FriendService;
import app.weave.notification.service.NotificationPayload;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.notification.service.NotificationPublisher;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareVisibility;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가시성 판정 canView + 피드 구독 알림.
 * 열람 판정은 visibility + 실시간 ACCEPTED friendship(+GROUP 멤버십)의 합성 — 친구 해제
 * 통지 경로가 없으므로(단방향) 읽기 시점 검증이 자동 회수다. 지연 정리는 없다.
 * 알림은 발행 시점에 구독자별 canView를 재평가해 "나에게 보이는 항목"만 보낸다.
 */
@Service
public class FeedService {

    private final FriendService friendService;
    private final FriendGroupService groupService;
    private final NotificationPublisher publisher;
    private final UserRepository userRepository;

    public FeedService(FriendService friendService, FriendGroupService groupService,
            NotificationPublisher publisher, UserRepository userRepository) {
        this.friendService = friendService;
        this.groupService = groupService;
        this.publisher = publisher;
        this.userRepository = userRepository;
    }

    /**
     * viewer가 owner의 항목을 볼 수 있는가.
     * FRIENDS·PUBLIC — ACCEPTED 친구 (PUBLIC의 발견 피드는 후속, V2 노출은 친구뿐).
     * GROUP — 친구 ∧ 그룹 멤버. group_id가 NULL(그룹 삭제)이면 소유자 외 불가 = 비공개 취급.
     * PRIVATE — 소유자만.
     */
    @Transactional(readOnly = true)
    public boolean canView(Long viewerId, Long ownerId, ShareVisibility visibility,
            Long groupId) {
        if (viewerId.equals(ownerId)) {
            return true;
        }
        return switch (visibility) {
            case PRIVATE -> false;
            case FRIENDS, PUBLIC -> friendService.isFriend(viewerId, ownerId);
            case GROUP -> groupId != null && friendService.isFriend(viewerId, ownerId)
                    && groupService.isGroupMember(groupId, viewerId);
        };
    }

    @Transactional(readOnly = true)
    public boolean canView(Long viewerId, Share share) {
        return canView(viewerId, share.getOwnerId(), share.getVisibility(),
                share.getGroupId());
    }

    /**
     * upsert 후 구독 알림 — owner의 구독자 중 "이전엔 못 보던 사람이 이번 설정으로 보이게 된"
     * 대상에게만 SHARED_ITEM을 발행한다 (신규 공개·범위 상향·그룹 변경 모두 이 경로).
     * 비공개 전환·변화 없는 재저장은 새로 보이는 대상이 없어 알림이 나가지 않는다.
     */
    @Transactional
    public void notifyNewlyVisible(Share share, String itemTitle,
            Set<Long> previouslyVisibleSubscriberIds) {
        UserRef owner = ownerRef(share.getOwnerId());
        for (Long subscriberId : groupService.subscriberIdsOf(share.getOwnerId())) {
            if (previouslyVisibleSubscriberIds.contains(subscriberId)) {
                continue;
            }
            if (canView(subscriberId, share)) {
                publisher.publish(subscriberId, new NotificationPayload.SharedItem(
                        share.getId(), share.getItemType().name(), share.getItemId(), owner,
                        itemTitle == null ? "" : itemTitle));
            }
        }
    }

    /** 특정 가시성 상태에서 지금 볼 수 있는 구독자 집합 — upsert 전후 비교의 before 계산용. */
    @Transactional(readOnly = true)
    public Set<Long> visibleSubscribers(Long ownerId, ShareVisibility visibility,
            Long groupId) {
        Set<Long> visible = new HashSet<>();
        for (Long subscriberId : groupService.subscriberIdsOf(ownerId)) {
            if (canView(subscriberId, ownerId, visibility, groupId)) {
                visible.add(subscriberId);
            }
        }
        return visible;
    }

    private UserRef ownerRef(Long ownerId) {
        String nickname = userRepository.findById(ownerId)
                .map(User::getNickname).orElse(null);
        return new UserRef(ownerId, nickname);
    }
}
