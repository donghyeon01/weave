package app.weave.friend.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedSubscriptionRepository
        extends JpaRepository<FeedSubscription, FeedSubscriptionId> {

    /** 특정 사용자를 구독하는 구독자 행 — 공개 발행 시점 알림 대상 원천. */
    List<FeedSubscription> findByFriendUserId(Long friendUserId);

    boolean existsBySubscriberIdAndFriendUserId(Long subscriberId, Long friendUserId);

    void deleteBySubscriberIdAndFriendUserId(Long subscriberId, Long friendUserId);
}
