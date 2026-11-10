package app.weave.share.domain;

import java.io.Serializable;
import java.util.Objects;

public class ShareTargetId implements Serializable {

    private Long shareId;
    private Long userId;

    protected ShareTargetId() {
    }

    public ShareTargetId(Long shareId, Long userId) {
        this.shareId = shareId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShareTargetId other)) {
            return false;
        }
        return Objects.equals(shareId, other.shareId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shareId, userId);
    }
}
