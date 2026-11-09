package app.weave.chat.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByProposalId(Long proposalId);

    /**
     * send·closeRoom 직렬화 잠금 — 두 경로가 같은 방 행을 FOR UPDATE로 획득해
     * CLOSED 전이와 메시지 INSERT가 교차하지 않는다 (ProposalService 선례).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.proposalId = :proposalId")
    Optional<ChatRoom> findByProposalIdForUpdate(@Param("proposalId") Long proposalId);

    /** 내 방 목록 (OPEN + CLOSED) — 비연관 엔터티 ON 조인 (id만 보유하는 프로젝트 관례). */
    @Query("select r from ChatRoom r join ChatRoomMember m on r.id = m.roomId "
            + "where m.userId = :me")
    List<ChatRoom> findRoomsByMember(@Param("me") Long me);
}
