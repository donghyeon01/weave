package app.weave.scheduling.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleProposalOccurrenceRepository
        extends JpaRepository<ScheduleProposalOccurrence, Long> {

    List<ScheduleProposalOccurrence> findByProposalIdOrderBySeqAsc(Long proposalId);

    /** 목록 배치 로드 — 제안별 그룹핑은 호출자 몫 (participants와 같은 N+1 방지 패턴). */
    List<ScheduleProposalOccurrence> findByProposalIdIn(Collection<Long> proposalIds);
}
