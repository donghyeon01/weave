package app.weave.friend.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.friend.domain.FeedSubscription;
import app.weave.friend.domain.FeedSubscriptionRepository;
import app.weave.friend.domain.FriendGroup;
import app.weave.friend.domain.FriendGroupMember;
import app.weave.friend.domain.FriendGroupMemberRepository;
import app.weave.friend.domain.FriendGroupRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 그룹(최대 3개)·피드 구독 — friend 모듈 소유.
 * 멤버십·구독 판정 메서드는 share의 가시성 검증이 소비한다 (share→friend 단방향).
 */
@Service
public class FriendGroupService {

    private static final int MAX_GROUPS = 3;

    /** 그룹 뷰 — 멤버 user_id 목록 포함 (계약 §3.6 확장분). */
    public record GroupView(Long groupId, String name, List<Long> memberIds) {
    }

    private final FriendGroupRepository groupRepository;
    private final FriendGroupMemberRepository memberRepository;
    private final FeedSubscriptionRepository subscriptionRepository;
    private final FriendService friendService;

    public FriendGroupService(FriendGroupRepository groupRepository,
            FriendGroupMemberRepository memberRepository,
            FeedSubscriptionRepository subscriptionRepository, FriendService friendService) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.friendService = friendService;
    }

    @Transactional(readOnly = true)
    public List<GroupView> myGroups(Long me) {
        List<FriendGroup> groups = groupRepository.findByOwnerIdOrderByIdAsc(me);
        Map<Long, List<Long>> members = memberRepository
                .findByGroupIdIn(groups.stream().map(FriendGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(FriendGroupMember::getGroupId,
                        Collectors.mapping(FriendGroupMember::getUserId, Collectors.toList())));
        return groups.stream()
                .map(g -> new GroupView(g.getId(), g.getName(),
                        members.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    @Transactional
    public GroupView create(Long me, String name, List<Long> memberIds) {
        // 스펙상 앱 레벨 검증 — count→insert 사이 동시 생성 경합으로 4개째가 들어갈 수 있으나
        // 허용된 race window다 (잠금·직렬화는 도입하지 않음)
        if (groupRepository.countByOwnerId(me) >= MAX_GROUPS) {
            throw new BusinessException(ErrorCode.GROUP_LIMIT_EXCEEDED);
        }
        FriendGroup saved = groupRepository.save(new FriendGroup(me, name));
        replaceMembersInternal(saved, me, memberIds);
        return new GroupView(saved.getId(), saved.getName(),
                memberIdsOf(saved.getId()));
    }

    @Transactional
    public GroupView rename(Long me, Long groupId, String name) {
        FriendGroup group = ownedGroup(me, groupId);
        group.rename(name);
        return new GroupView(group.getId(), group.getName(), memberIdsOf(groupId));
    }

    /**
     * 그룹 삭제 — 행 삭제는 멤버 CASCADE로 연쇄된다. 이 그룹을 참조하는 shares의
     * group_id는 FK ON DELETE SET NULL로 비워져 해당 항목이 비공개 취급된다.
     */
    @Transactional
    public void delete(Long me, Long groupId) {
        groupRepository.delete(ownedGroup(me, groupId));
    }

    /** 멤버 전량 교체 — 전원 현재 ACCEPTED 친구여야 하고 본인 포함 불가. */
    @Transactional
    public GroupView replaceMembers(Long me, Long groupId, List<Long> memberIds) {
        FriendGroup group = ownedGroup(me, groupId);
        replaceMembersInternal(group, me, memberIds);
        return new GroupView(group.getId(), group.getName(), memberIdsOf(groupId));
    }

    /** 피드 알람 구독 — 친구만 가능. 비친구·본인은 SUBSCRIPTION-INVALID(400). */
    @Transactional
    public void subscribe(Long me, Long friendUserId) {
        if (me.equals(friendUserId) || !friendService.isFriend(me, friendUserId)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID);
        }
        if (!subscriptionRepository.existsBySubscriberIdAndFriendUserId(me, friendUserId)) {
            subscriptionRepository.save(new FeedSubscription(me, friendUserId));
        }
    }

    @Transactional
    public void unsubscribe(Long me, Long friendUserId) {
        subscriptionRepository.deleteBySubscriberIdAndFriendUserId(me, friendUserId);
    }

    /** 내가 구독 중인 친구 user_id 목록 — fe 토글 초기 상태용 (계약 §3.6). */
    @Transactional(readOnly = true)
    public List<Long> subscribedIds(Long me) {
        return subscriptionRepository.findBySubscriberId(me).stream()
                .map(FeedSubscription::getFriendUserId).toList();
    }

    // --- share 모듈이 소비하는 판정 ---

    /** owner가 groupId를 소유하는지 — GROUP 공개 설정의 groupId 검증. */
    @Transactional(readOnly = true)
    public boolean ownsGroup(Long ownerId, Long groupId) {
        return groupId != null
                && groupRepository.findByIdAndOwnerId(groupId, ownerId).isPresent();
    }

    /** GROUP 공개 항목의 멤버십 판정 — friendship은 호출 측(canView)에서 별도 검증한다. */
    @Transactional(readOnly = true)
    public boolean isGroupMember(Long groupId, Long userId) {
        return memberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    /** owner를 구독하는 구독자 id 목록 — SHARED_ITEM 발행 대상 후보. */
    @Transactional(readOnly = true)
    public List<Long> subscriberIdsOf(Long ownerId) {
        return subscriptionRepository.findByFriendUserId(ownerId).stream()
                .map(FeedSubscription::getSubscriberId).toList();
    }

    private FriendGroup ownedGroup(Long me, Long groupId) {
        return groupRepository.findByIdAndOwnerId(groupId, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private void replaceMembersInternal(FriendGroup group, Long me, List<Long> memberIds) {
        List<Long> requested = memberIds == null
                ? List.of() : new ArrayList<>(new LinkedHashSet<>(memberIds));
        if (requested.contains(me)
                || !friendService.acceptedFriendIds(me).containsAll(requested)) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_INVALID);
        }
        memberRepository.deleteByGroupId(group.getId());
        if (!requested.isEmpty()) {
            memberRepository.saveAll(requested.stream()
                    .map(uid -> new FriendGroupMember(group.getId(), uid)).toList());
        }
    }

    private List<Long> memberIdsOf(Long groupId) {
        return memberRepository.findByGroupIdOrderByUserIdAsc(groupId).stream()
                .map(FriendGroupMember::getUserId).toList();
    }
}
