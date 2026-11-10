package app.weave.chat.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 방 목록 카드 프리뷰용 — 방별 최신 메시지 id 배치 집계 행. */
    interface LastIdRow {
        Long getRoomId();
        Long getLastId();
    }

    /** 커서 페이지네이션 — id < cursor 구간을 최신순으로 limit건 (idx_chat_messages_room_id). */
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor,
            Pageable pageable);

    /** 첫 페이지 — 최신순 limit건. Top 없이 Pageable로만 제한한다 (findTop은 1건 고정). */
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    /** 방 N개의 최신 메시지를 GROUP BY 한 번으로 집계 — 목록의 방별 lastMessage 원천. */
    @Query("select m.roomId as roomId, max(m.id) as lastId from ChatMessage m "
            + "where m.roomId in :roomIds group by m.roomId")
    List<LastIdRow> findLastIdByRoomIds(@Param("roomIds") List<Long> roomIds);

    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId,
            String clientMessageId);

    boolean existsByRoomIdAndId(Long roomId, Long id);
}
