package app.weave.friend.domain;

import java.io.Serializable;
import java.util.Objects;

public class FeedSubscriptionId implements Serializable {

    private Long subscriberId;
    private Long friendUserId;

    protected FeedSubscriptionId() {
    }

    public FeedSubscriptionId(Long subscriberId, Long friendUserId) {
        this.subscriberId = subscriberId;
        this.friendUserId = friendUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeedSubscriptionId other)) {
            return false;
        }
        return Objects.equals(subscriberId, other.subscriberId)
                && Objects.equals(friendUserId, other.friendUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberId, friendUserId);
    }
}
