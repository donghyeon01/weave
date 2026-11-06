package app.weave.scheduling.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleProposalParticipantRepository
        extends JpaRepository<ScheduleProposalParticipant, Long> {

    List<ScheduleProposalParticipant> findByProposalId(Long proposalId);

    List<ScheduleProposalParticipant> findByProposalIdIn(List<Long> proposalIds);

    Optional<ScheduleProposalParticipant> findByProposalIdAndUserId(Long proposalId, Long userId);
}
