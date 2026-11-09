package app.weave.chat.domain;

import java.io.Serializable;
import java.util.Objects;

public class ChatRoomMemberId implements Serializable {

    private Long roomId;
    private Long userId;

    protected ChatRoomMemberId() {
    }

    public ChatRoomMemberId(Long roomId, Long userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatRoomMemberId other)) {
            return false;
        }
        return Objects.equals(roomId, other.roomId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
