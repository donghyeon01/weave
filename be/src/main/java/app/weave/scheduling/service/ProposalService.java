package app.weave.scheduling.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.chat.service.ChatService;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.event.domain.Event;
import app.weave.event.domain.Event.Source;
import app.weave.event.domain.EventRepository;
import app.weave.friend.domain.FriendshipRepository;
import app.weave.notification.service.NotificationPayload;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.notification.service.NotificationPublisher;
import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.domain.ScheduleProposal;
import app.weave.scheduling.domain.ScheduleProposal.Status;
import app.weave.scheduling.domain.ScheduleProposalParticipant;
import app.weave.scheduling.domain.ScheduleProposalParticipant.Response;
import app.weave.scheduling.domain.ScheduleProposalParticipantRepository;
import app.weave.scheduling.domain.ScheduleProposalOccurrence;
import app.weave.scheduling.domain.ScheduleProposalOccurrenceRepository;
import app.weave.scheduling.domain.ScheduleProposalRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {

    // 계산량·알림·채팅방 규모 상한 — 계약 §3.7 (recommend §3.8과 동일 기준)
    private static final int MAX_PARTICIPANTS = 50;
    private static final int MAX_DATE_SLOT_DAYS = 30;

    /** 제안 + 응답 현황 + 표시용 사용자 정보 — 컨트롤러가 ProposalResponse로 변환한다. */
    public record ProposalDetail(ScheduleProposal proposal,
            List<ScheduleProposalParticipant> participants, User organizer,
            Map<Long, User> users, List<ScheduleProposalOccurrence> occurrences) {
    }

    private final ScheduleProposalRepository proposalRepository;
    private final ScheduleProposalParticipantRepository participantRepository;
    private final ScheduleProposalOccurrenceRepository occurrenceRepository;
    private final FriendshipRepository friendshipRepository;
    private final EventRepository eventRepository;
    private final NotificationPublisher publisher;
    private final UserRepository userRepository;
    private final ChatService chatService;

    public ProposalService(ScheduleProposalRepository proposalRepository,
            ScheduleProposalParticipantRepository participantRepository,
            ScheduleProposalOccurrenceRepository occurrenceRepository,
            FriendshipRepository friendshipRepository, EventRepository eventRepository,
            NotificationPublisher publisher, UserRepository userRepository,
            ChatService chatService) {
        this.proposalRepository = proposalRepository;
        this.participantRepository = participantRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.friendshipRepository = friendshipRepository;
        this.eventRepository = eventRepository;
        this.publisher = publisher;
        this.userRepository = userRepository;
        this.chatService = chatService;
    }

    @Transactional
    public ProposalDetail create(Long me, String title, Slot slot,
            RecurrenceExpander.Spec recurrence, List<Long> participantIds,
            String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = proposalRepository.findByOrganizerIdAndIdempotencyKey(me,
                    idempotencyKey);
            if (existing.isPresent()) {
                return detailOf(existing.get());
            }
        }
        if (participantIds == null || participantIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        // 중복 id는 participants UK·chat_room_members PK 충돌로 500이 나므로 미리 제거한다
        participantIds = participantIds.stream().distinct().toList();
        if (participantIds.size() > MAX_PARTICIPANTS) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        if (!acceptedFriendIds(me).containsAll(participantIds)) {
            throw new BusinessException(ErrorCode.SCHEDULING_NOT_FRIEND);
        }
        LocalDateTime[] range = toRange(slot);
        if (!range[0].isBefore(range[1])) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        // DATE 슬롯의 점유 일수 상한 — recommend durationDays 상한과 정합 (계약 §3.7)
        if (slot instanceof Slot.Days d
                && ChronoUnit.DAYS.between(d.startDate(), d.endDate()) + 1
                        > MAX_DATE_SLOT_DAYS) {
            throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
        }
        // 정례는 TIME 전용이고 첫 회차 날짜의 ISO 요일이 byDay에 포함돼야 한다 (계약 §3.7)
        List<LocalDate> occurrenceDates = null;
        if (recurrence != null) {
            if (!(slot instanceof Slot.Time t)
                    || !recurrence.byDays().contains(t.date().getDayOfWeek().getValue())) {
                throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
            }
            occurrenceDates = RecurrenceExpander.expand(t.date(), recurrence);
        }
        ScheduleProposal proposal = proposalRepository.save(new ScheduleProposal(me, title,
                modeOf(slot), range[0], range[1], idempotencyKey));
        if (recurrence != null) {
            // start_time/end_time은 첫 회차 범위 그대로 — 방 스냅샷·알림 호환
            proposal.applyRecurrence(recurrence.freq(), recurrence.interval(),
                    recurrence.byDays(), recurrence.count());
        }
        // 전 제안 회차 물질화 — 비정례도 seq=0 한 행 (confirm이 이 행들로 이벤트를 만든다)
        occurrenceRepository.saveAll(materialize(proposal.getId(), slot, range,
                occurrenceDates));
        participantRepository.saveAll(participantIds.stream()
                .map(uid -> new ScheduleProposalParticipant(proposal.getId(), uid)).toList());

        // 참석 총원 3명+(방장+참여자 2)이면 제안 범위 단체채팅 방을 같은 트랜잭션에 생성한다 —
        // 방 생성이 제안의 일부라 실패 시 제안 생성도 롤백된다 (계약 §3.10).
        if (participantIds.size() >= 2) {
            List<Long> roster = new ArrayList<>();
            roster.add(me);
            roster.addAll(participantIds);
            chatService.createRoom(proposal.getId(), proposal.getTitle(),
                    mapMode(proposal.getMode()), proposal.getStartTime(), proposal.getEndTime(),
                    roster, occurrenceCountOf(proposal));
        }

        User organizer = loadUser(me);
        var payload = new NotificationPayload.ProposalReceived(proposal.getId(), title,
                new UserRef(me, organizer.getNickname()), toPayloadSlot(slot),
                occurrenceCountOf(proposal));
        for (Long uid : participantIds) {
            publisher.publish(uid, payload);
        }
        return detailOf(proposal);
    }

    /**
     * 응답 — FOR UPDATE 잠금으로 직렬화 (ADR-0004).
     * 전원 수락은 자동 확정하지 않고 ALL_ACCEPTED 게이트로 둔다 (ADR-0011) — 확정은 방장 confirm.
     */
    @Transactional
    public ProposalDetail respond(Long me, Long proposalId, Response response) {
        ScheduleProposal proposal = proposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        if (proposal.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_PENDING);
        }
        ScheduleProposalParticipant mine = participantRepository
                .findByProposalIdAndUserId(proposalId, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_PARTICIPANT));
        // 계약 §3.8 — ACCEPTED|DECLINED만 허용한다. PENDING으로 되돌리는 응답 철회는 거부.
        if (response == null || response == Response.PENDING) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        mine.respond(response);

        List<ScheduleProposalParticipant> all = participantRepository
                .findByProposalId(proposalId);
        if (all.stream().allMatch(r -> r.getResponse() == Response.ACCEPTED)) {
            proposal.markAllAccepted();
            User organizer = loadUser(proposal.getOrganizerId());
            publisher.publish(proposal.getOrganizerId(),
                    new NotificationPayload.ProposalAllAccepted(proposal.getId(),
                            proposal.getTitle(),
                            new UserRef(organizer.getId(), organizer.getNickname()),
                            toPayloadSlot(proposal.toSlot()),
                            occurrenceCountOf(proposal)));
        } else if (all.stream().anyMatch(r -> r.getResponse() == Response.DECLINED)) {
            proposal.decline();
            chatService.closeRoom(proposalId);
            User decliner = loadUser(me);
            publisher.publish(proposal.getOrganizerId(),
                    new NotificationPayload.ProposalDeclined(proposal.getId(),
                            proposal.getTitle(),
                            new UserRef(me, decliner.getNickname())));
        }
        return detailOf(proposal);
    }

    /**
     * 확정 — 방장 전용, ALL_ACCEPTED 게이트 통과 시에만 (ADR-0011).
     * 제안 잠금 → 참석자 user 행을 id 오름차순 잠금(EventService 쓰기와 같은 잠금 규약,
     * 교착 방지를 위해 반드시 제안 잠금 이후·오름차순) → HARD 재검증 → 확정.
     * status + 전원 EVENTS + 전원 알림 INSERT가 단일 트랜잭션으로 원자다 (ADR-0004).
     * WS push만 AFTER_COMMIT으로 빠진다 (Publisher 내부).
     * READ_COMMITTED — user 잠금 대기 후 읽는 이벤트 조회가 최신 커밋을 봐야 한다
     * (REPEATABLE READ 스냅샷 오판 방지 — PR #24 refresh 회전과 같은 교훈).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProposalDetail confirmProposal(Long me, Long proposalId) {
        ScheduleProposal proposal = proposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        if (!proposal.getOrganizerId().equals(me)) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        if (proposal.getStatus() != Status.ALL_ACCEPTED) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_AWAITING_CONFIRM);
        }

        List<ScheduleProposalParticipant> all = participantRepository
                .findByProposalId(proposalId);
        List<Long> attendeeIds = new ArrayList<>(
                all.stream().map(ScheduleProposalParticipant::getUserId).toList());
        attendeeIds.add(proposal.getOrganizerId());
        attendeeIds = attendeeIds.stream().distinct().sorted().toList();
        // 참석자 user 행 잠금 — EventService create/update/delete가 같은 행을 잠그므로
        // confirm과 개인 일정 쓰기가 직렬화된다. FK CASCADE로 행 존재가 보장되고,
        // empty면 잠글 대상도 그 사용자의 이벤트도 없으므로 건너뛴다.
        for (Long uid : attendeeIds) {
            userRepository.findByIdForUpdate(uid);
        }

        // HARD 재검증 — 회차 전체 범위와 겹치는 이벤트를 일괄 조회해 HARD만 검사한다
        // (SOFT·FREE·가용성 선언·선호는 수락한 약속을 막지 않는다 — spec D5).
        List<ScheduleProposalOccurrence> occurrences = occurrenceRepository
                .findByProposalIdOrderBySeqAsc(proposalId);
        LocalDateTime minStart = occurrences.stream()
                .map(ScheduleProposalOccurrence::getStartTime)
                .min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime maxEnd = occurrences.stream()
                .map(ScheduleProposalOccurrence::getEndTime)
                .max(LocalDateTime::compareTo).orElseThrow();
        Map<Long, List<Event>> hardByUser = new HashMap<>();
        for (Event e : eventRepository
                .findByUserIdInAndStartTimeLessThanAndEndTimeGreaterThan(
                        attendeeIds, maxEnd, minStart)) {
            if (e.getBusyPolicy() == Event.BusyPolicy.HARD) {
                hardByUser.computeIfAbsent(e.getUserId(), k -> new ArrayList<>()).add(e);
            }
        }
        for (ScheduleProposalOccurrence occ : occurrences) {
            for (Long uid : attendeeIds) {
                for (Event e : hardByUser.getOrDefault(uid, List.of())) {
                    // 반열린 구간 겹침 — 맞닿음(end==start)은 충돌 아님
                    if (e.getStartTime().isBefore(occ.getEndTime())
                            && e.getEndTime().isAfter(occ.getStartTime())) {
                        throw new BusinessException(ErrorCode.PROPOSAL_CONFLICT);
                    }
                }
            }
        }

        proposal.confirm();
        chatService.closeRoom(proposalId);

        boolean allDay = proposal.getMode() == Mode.DATE;
        List<Event> events = new ArrayList<>();
        for (ScheduleProposalOccurrence occ : occurrences) {
            for (Long uid : attendeeIds) {
                // 확정 일정은 조율 불가 — busyPolicy=HARD 명시 (개인 사본은 이후 본인만 SOFT 변경 가능)
                events.add(new Event(uid, proposal.getTitle(), null,
                        occ.getStartTime(), occ.getEndTime(), null, allDay,
                        Source.PROPOSAL, proposal.getId(), Event.BusyPolicy.HARD, null));
            }
        }
        eventRepository.saveAll(events);
        var confirmed = new NotificationPayload.ProposalConfirmed(proposal.getId(),
                proposal.getTitle(), toPayloadSlot(proposal.toSlot()),
                occurrenceCountOf(proposal));
        for (Long uid : attendeeIds) {
            publisher.publish(uid, confirmed);
        }
        return detailOf(proposal);
    }

    /** 취소 — respond와 같은 FOR UPDATE 잠금. 확정 직후 취소 덮어쓰기 경합을 잠금으로 직렬화한다. */
    @Transactional
    public ProposalDetail cancel(Long me, Long proposalId) {
        ScheduleProposal proposal = proposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        if (!proposal.getOrganizerId().equals(me)) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        // 게이트(ALL_ACCEPTED)도 "아직 늦지 않은" 상태 — 방장 취소 허용 (ADR-0011)
        if (proposal.getStatus() != Status.PENDING
                && proposal.getStatus() != Status.ALL_ACCEPTED) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_PENDING);
        }
        proposal.cancel();
        chatService.closeRoom(proposalId);
        return detailOf(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalDetail get(Long me, Long proposalId) {
        ScheduleProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        boolean involved = proposal.getOrganizerId().equals(me)
                || participantRepository.findByProposalIdAndUserId(proposalId, me).isPresent();
        if (!involved) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        return detailOf(proposal);
    }

    @Transactional(readOnly = true)
    public Page<ProposalDetail> box(Long me, String box, Pageable pageable) {
        Page<ScheduleProposal> page = switch (box) {
            case "received" -> proposalRepository.findReceivedByParticipant(me, pageable);
            case "sent" -> proposalRepository.findByOrganizerIdOrderByCreatedAtDesc(me, pageable);
            default -> throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        };
        return new PageImpl<>(detailsOf(page.getContent()), pageable, page.getTotalElements());
    }

    private ProposalDetail detailOf(ScheduleProposal proposal) {
        List<ScheduleProposalParticipant> participants = participantRepository
                .findByProposalId(proposal.getId());
        User organizer = loadUser(proposal.getOrganizerId());
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(participants.stream()
                        .map(ScheduleProposalParticipant::getUserId).toList())
                .forEach(u -> users.put(u.getId(), u));
        return new ProposalDetail(proposal, participants, organizer, users,
                occurrenceRepository.findByProposalIdOrderBySeqAsc(proposal.getId()));
    }

    /** 목록 페이지용 배치 detail — 행별 N+1을 피하기 위해 참여자·사용자를 일괄 조회한다. */
    public List<ProposalDetail> detailsOf(List<ScheduleProposal> proposals) {
        List<Long> ids = proposals.stream().map(ScheduleProposal::getId).toList();
        Map<Long, List<ScheduleProposalParticipant>> byProposal = new HashMap<>();
        for (ScheduleProposalParticipant pp : participantRepository.findByProposalIdIn(ids)) {
            byProposal.computeIfAbsent(pp.getProposalId(), k -> new ArrayList<>()).add(pp);
        }
        Set<Long> userIds = new HashSet<>();
        for (ScheduleProposal p : proposals) {
            userIds.add(p.getOrganizerId());
            byProposal.getOrDefault(p.getId(), List.of())
                    .forEach(pp -> userIds.add(pp.getUserId()));
        }
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));
        Map<Long, List<ScheduleProposalOccurrence>> occByProposal = new HashMap<>();
        for (ScheduleProposalOccurrence o : occurrenceRepository.findByProposalIdIn(ids)) {
            occByProposal.computeIfAbsent(o.getProposalId(), k -> new ArrayList<>()).add(o);
        }
        occByProposal.values().forEach(l -> l.sort(
                java.util.Comparator.comparingInt(ScheduleProposalOccurrence::getSeq)));
        return proposals.stream()
                .map(p -> new ProposalDetail(p,
                        byProposal.getOrDefault(p.getId(), List.of()),
                        users.get(p.getOrganizerId()), users,
                        occByProposal.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    private Set<Long> acceptedFriendIds(Long me) {
        return new HashSet<>(friendshipRepository.findAcceptedFriendships(me)
                .stream().map(f -> f.counterpartyOf(me)).toList());
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    /** 회차 행 조립 — 정례는 전개 날짜×슬롯 시각, 비정례는 저장 범위 그대로 seq=0 한 행. */
    private List<ScheduleProposalOccurrence> materialize(Long proposalId, Slot slot,
            LocalDateTime[] range, List<LocalDate> occurrenceDates) {
        List<ScheduleProposalOccurrence> occs = new ArrayList<>();
        if (occurrenceDates == null) {
            occs.add(new ScheduleProposalOccurrence(proposalId, 0, range[0], range[1]));
            return occs;
        }
        Slot.Time t = (Slot.Time) slot;
        for (int i = 0; i < occurrenceDates.size(); i++) {
            LocalDate d = occurrenceDates.get(i);
            occs.add(new ScheduleProposalOccurrence(proposalId, i,
                    d.atTime(t.startTime()), d.atTime(t.endTime())));
        }
        return occs;
    }

    /** API Slot → 행 저장 규약: DATE는 endDate 포함 끝을 +1일 자정 배타 상한으로 저장한다. */
    private LocalDateTime[] toRange(Slot slot) {
        return switch (slot) {
            case Slot.Time t -> new LocalDateTime[]{
                    t.date().atTime(t.startTime()), t.date().atTime(t.endTime())};
            case Slot.Days d -> new LocalDateTime[]{
                    d.startDate().atStartOfDay(), d.endDate().plusDays(1).atStartOfDay()};
        };
    }

    /** 방 스냅샷·알림 payload 공용 — 비정례 제안은 occurrenceCount가 null이라 1로 대체한다. */
    private int occurrenceCountOf(ScheduleProposal proposal) {
        return proposal.getOccurrenceCount() != null ? proposal.getOccurrenceCount() : 1;
    }

    private Mode modeOf(Slot slot) {
        return slot instanceof Slot.Time ? Mode.TIME : Mode.DATE;
    }

    /** scheduling Mode → chat Mode — 값만 같고 타입은 모듈별로 분리되어 이름으로 매핑한다. */
    private app.weave.chat.domain.Mode mapMode(Mode mode) {
        return app.weave.chat.domain.Mode.valueOf(mode.name());
    }

    /** scheduling Slot → 알림 payload Slot 변환 (계약 §3.7 payload와 동형). */
    private NotificationPayload.Slot toPayloadSlot(Slot slot) {
        return switch (slot) {
            case Slot.Time t -> new NotificationPayload.Slot.Time(t.date(), t.startTime(),
                    t.endTime());
            case Slot.Days d -> new NotificationPayload.Slot.Days(d.startDate(), d.endDate());
        };
    }
}
