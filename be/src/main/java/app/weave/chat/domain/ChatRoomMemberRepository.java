package app.weave.chat.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {

    /** openRooms의 방별 unread 집계 행. */
    interface UnreadRow {
        Long getRoomId();
        Long getUnread();
    }

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatRoomMember> findByRoomId(Long roomId);

    /**
     * 읽음 커서 조건부 갱신 — last_read가 NULL이거나 더 작을 때만 UPDATE해
     * 동시 markRead 경합에서 커서 역행을 DB 단에서 차단한다 (0행 갱신 = no-op).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatRoomMember m set m.lastReadMessageId = :newId "
            + "where m.roomId = :roomId and m.userId = :userId "
            + "and (m.lastReadMessageId is null or m.lastReadMessageId < :newId)")
    int advanceReadCursor(@Param("roomId") Long roomId, @Param("userId") Long userId,
            @Param("newId") Long newId);

    /**
     * 방별 unread 1회 집계 — 커서는 멤버 행의 last_read를 직접 참조해
     * 방 N개의 COUNT 쿼리를 GROUP BY 한 번으로 대체한다.
     */
    @Query("select m.roomId as roomId, count(msg.id) as unread "
            + "from ChatRoomMember m left join ChatMessage msg "
            + "on msg.roomId = m.roomId and msg.id > coalesce(m.lastReadMessageId, 0) "
            + "and msg.senderId <> :me "
            + "where m.userId = :me and m.roomId in :roomIds group by m.roomId")
    List<UnreadRow> countUnreadByRoom(@Param("me") Long me,
            @Param("roomIds") List<Long> roomIds);
}
