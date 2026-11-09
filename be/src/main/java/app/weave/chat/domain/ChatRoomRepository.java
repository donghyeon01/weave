package app.weave.chat.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByProposalId(Long proposalId);

    /** 내 OPEN 방 목록 — 비연관 엔터티 ON 조인 (id만 보유하는 프로젝트 관례). */
    @Query("select r from ChatRoom r join ChatRoomMember m on r.id = m.roomId "
            + "where m.userId = :me and r.status = 'OPEN'")
    List<ChatRoom> findOpenRoomsByMember(@Param("me") Long me);
}
