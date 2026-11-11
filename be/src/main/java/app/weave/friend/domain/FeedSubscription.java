package app.weave.friend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

/**
 * 피드 알람 구독 — subscriber가 friendUser의 "나에게 보이는" 신규 공개를 알림받는다.
 * 친구 해제로 행을 지우지 않는다 — 발행 시점 canView 재평가가 비가시를 보장하고
 * 재친구 시 구독이 자연 복원된다 (spec §설계 결정).
 */
@Entity
@Table(name = "feed_subscriptions")
@IdClass(FeedSubscriptionId.class)
public class FeedSubscription implements Persistable<FeedSubscriptionId> {

    /** 복합키 엔터티는 save가 merge로 판별해 SELECT+UPDATE — isNew로 INSERT 경로를 연다. */
    @Transient
    private boolean isNew = true;

    @Id
    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    @Id
    @Column(name = "friend_user_id", nullable = false)
    private Long friendUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected FeedSubscription() {
    }

    public FeedSubscription(Long subscriberId, Long friendUserId) {
        this.subscriberId = subscriberId;
        this.friendUserId = friendUserId;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.isNew = false;
    }

    @Override
    public FeedSubscriptionId getId() {
        return new FeedSubscriptionId(subscriberId, friendUserId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getSubscriberId() { return subscriberId; }
    public Long getFriendUserId() { return friendUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
