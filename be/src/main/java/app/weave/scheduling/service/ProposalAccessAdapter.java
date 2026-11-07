package app.weave.scheduling.service;

import app.weave.common.port.ProposalAccess;
import app.weave.common.port.ProposalRoster;
import app.weave.scheduling.domain.ScheduleProposal;
import app.weave.scheduling.domain.ScheduleProposalParticipant;
import app.weave.scheduling.domain.ScheduleProposalParticipantRepository;
import app.weave.scheduling.domain.ScheduleProposalRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** ProposalAccess 구현 — 제안 로스터의 읽기 전용 출구 (capability-map 조회 포트 규약). */
@Component
public class ProposalAccessAdapter implements ProposalAccess {

    private final ScheduleProposalRepository proposalRepository;
    private final ScheduleProposalParticipantRepository participantRepository;

    public ProposalAccessAdapter(ScheduleProposalRepository proposalRepository,
            ScheduleProposalParticipantRepository participantRepository) {
        this.proposalRepository = proposalRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public Map<Long, ProposalRoster> rosters(Collection<Long> proposalIds) {
        if (proposalIds == null || proposalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> organizers = new HashMap<>();
        proposalRepository.findAllById(proposalIds)
                .forEach(p -> organizers.put(p.getId(), p.getOrganizerId()));
        Map<Long, Set<Long>> participants = new HashMap<>();
        for (ScheduleProposalParticipant pp : participantRepository
                .findByProposalIdIn(List.copyOf(proposalIds))) {
            participants.computeIfAbsent(pp.getProposalId(), k -> new java.util.HashSet<>())
                    .add(pp.getUserId());
        }
        return organizers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> new ProposalRoster(e.getValue(),
                        participants.getOrDefault(e.getKey(), Set.of()))));
    }
}
