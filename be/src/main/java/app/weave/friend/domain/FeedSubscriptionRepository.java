package app.weave.friend.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedSubscriptionRepository
        extends JpaRepository<FeedSubscription, FeedSubscriptionId> {

    /** 특정 사용자를 구독하는 구독자 행 — 공개 발행 시점 알림 대상 원천. */
    List<FeedSubscription> findByFriendUserId(Long friendUserId);

    /** 내가 구독 중인 행 — 친구 프로필의 피드 알림 토글 상태 원천. */
    List<FeedSubscription> findBySubscriberId(Long subscriberId);

    boolean existsBySubscriberIdAndFriendUserId(Long subscriberId, Long friendUserId);

    void deleteBySubscriberIdAndFriendUserId(Long subscriberId, Long friendUserId);
}
