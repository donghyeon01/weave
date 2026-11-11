package app.weave.friend.domain;

import java.io.Serializable;
import java.util.Objects;

public class FriendGroupMemberId implements Serializable {

    private Long groupId;
    private Long userId;

    protected FriendGroupMemberId() {
    }

    public FriendGroupMemberId(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FriendGroupMemberId other)) {
            return false;
        }
        return Objects.equals(groupId, other.groupId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}
