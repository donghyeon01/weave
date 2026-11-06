package app.weave.scheduling.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleProposalRepository extends JpaRepository<ScheduleProposal, Long> {

    /** 응답·취소는 반드시 이 잠금 조회로 진입한다 — 확정 직후 취소 덮어쓰기 경합 방지 (ADR-0004). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ScheduleProposal p where p.id = :id")
    Optional<ScheduleProposal> findByIdForUpdate(@Param("id") Long id);

    Optional<ScheduleProposal> findByOrganizerIdAndIdempotencyKey(Long organizerId,
            String idempotencyKey);

    Page<ScheduleProposal> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId,
            Pageable pageable);

    /** received box — 내가 참여자인 제안. 비연관 엔터티 ON 조인 (id만 보유하는 프로젝트 관례). */
    @Query("select p from ScheduleProposal p join ScheduleProposalParticipant pp "
            + "on p.id = pp.proposalId where pp.userId = :me order by p.createdAt desc")
    Page<ScheduleProposal> findReceivedByParticipant(@Param("me") Long me, Pageable pageable);
}
