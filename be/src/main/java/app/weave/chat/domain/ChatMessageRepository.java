package app.weave.chat.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 커서 페이지네이션 — id < cursor 구간을 최신순으로 limit건 (idx_chat_messages_room_id). */
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor,
            Pageable pageable);

    /** 첫 페이지 — 최신순 limit건. Top 없이 Pageable로만 제한한다 (findTop은 1건 고정). */
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    Optional<ChatMessage> findByRoomIdAndClientMessageId(Long roomId, String clientMessageId);

    boolean existsByRoomIdAndId(Long roomId, Long id);
}
