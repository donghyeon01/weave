package app.weave.scheduling.api;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.scheduling.domain.ScheduleProposal;
import app.weave.scheduling.domain.ScheduleProposalParticipant;
import app.weave.scheduling.domain.ScheduleProposalParticipant.Response;
import app.weave.scheduling.service.ProposalService;
import app.weave.scheduling.service.ProposalService.ProposalDetail;
import app.weave.scheduling.service.RecurrenceExpander;
import app.weave.scheduling.service.Slot;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduling/proposals")
public class ProposalController {

    private final ProposalService service;

    public ProposalController(ProposalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProposalResponse> create(@AuthenticationPrincipal Long me,
            @Valid @RequestBody CreateRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RecurrenceExpander.Spec recurrence = null;
        if (req.recurrence() != null) {
            try {
                recurrence = RecurrenceExpander.Spec.of(req.recurrence().freq(),
                        req.recurrence().interval(), req.recurrence().byDay(),
                        req.recurrence().count());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.SCHEDULING_INVALID_INPUT);
            }
        }
        ProposalDetail detail = service.create(me, req.title(), req.slot(), recurrence,
                req.participantIds(), idempotencyKey);
        return ResponseEntity
                .created(URI.create("/api/scheduling/proposals/" + detail.proposal().getId()))
                .body(toResponse(detail));
    }

    @GetMapping
    public Page<ProposalSummary> box(@AuthenticationPrincipal Long me,
            @RequestParam String box, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ProposalDetail> details = service.box(me, box, pageable);
        return details.map(this::toSummary);
    }

    @GetMapping("/{id}")
    public ProposalResponse get(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        return toResponse(service.get(me, id));
    }

    @PostMapping("/{id}/respond")
    public ProposalResponse respond(@AuthenticationPrincipal Long me, @PathVariable Long id,
            @Valid @RequestBody RespondRequest req) {
        return toResponse(service.respond(me, id, req.response()));
    }

    @PostMapping("/{id}/confirm")
    public ProposalResponse confirm(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        return toResponse(service.confirmProposal(me, id));
    }

    @PostMapping("/{id}/cancel")
    public ProposalResponse cancel(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        return toResponse(service.cancel(me, id));
    }

    private ProposalResponse toResponse(ProposalDetail d) {
        var p = d.proposal();
        var organizer = d.organizer();
        return new ProposalResponse(p.getId(), p.getTitle(), p.getStatus().name(), p.toSlot(),
                new UserSummary(organizer.getId(), organizer.getNickname()),
                d.participants().stream()
                        .map(pp -> new ParticipantSummary(
                                new UserSummary(pp.getUserId(),
                                        d.users().containsKey(pp.getUserId())
                                                ? d.users().get(pp.getUserId()).getNickname()
                                                : null),
                                pp.getResponse().name(),
                                pp.getRespondedAt() != null ? pp.getRespondedAt().toString()
                                        : null))
                        .toList(),
                recurrenceOf(p), occurrenceCountOf(p), occurrencesOf(d),
                p.getCreatedAt().toString());
    }

    private ProposalSummary toSummary(ProposalDetail d) {
        var p = d.proposal();
        long acceptCount = d.participants().stream()
                .filter(pp -> pp.getResponse() == Response.ACCEPTED).count();
        return new ProposalSummary(p.getId(), p.getTitle(), p.getStatus().name(), p.toSlot(),
                new UserSummary(d.organizer().getId(), d.organizer().getNickname()),
                (int) acceptCount, d.participants().size(), recurrenceOf(p),
                occurrenceCountOf(p), p.getCreatedAt().toString());
    }

    private static RecurrenceInfo recurrenceOf(ScheduleProposal p) {
        if (p.getRecurrenceFreq() == null) {
            return null;
        }
        return new RecurrenceInfo(p.getRecurrenceFreq(), p.getRecurrenceInterval(),
                p.byDays().stream().sorted().toList(), p.getOccurrenceCount());
    }

    /** occurrence_count NULL(구버전 행)은 단일 제안과 같다 — 계약상 항상 int (§3.7). */
    private static int occurrenceCountOf(ScheduleProposal p) {
        return p.getOccurrenceCount() != null ? p.getOccurrenceCount() : 1;
    }

    /** occurrences는 정례만 노출 — 단일 제안은 null (계약 §3.7). */
    private static List<OccurrenceInfo> occurrencesOf(ProposalDetail d) {
        if (d.proposal().getRecurrenceFreq() == null) {
            return null;
        }
        return d.occurrences().stream()
                .map(o -> new OccurrenceInfo(o.getStartTime().toLocalDate(),
                        o.getStartTime().toLocalTime(), o.getEndTime().toLocalTime()))
                .toList();
    }

    public record CreateRequest(@NotBlank @Size(max = 200) String title, @NotNull Slot slot,
            @NotEmpty List<Long> participantIds, RecurrenceRequest recurrence) {
    }

    public record RespondRequest(@NotNull ScheduleProposalParticipant.Response response) {
    }

    public record UserSummary(Long id, String nickname) {
    }

    public record ParticipantSummary(UserSummary user, String response, String respondedAt) {
    }

    /** 정례 메타 — freq·interval·byDay·count. 단일 제안은 null (계약 §3.7). */
    public record RecurrenceInfo(String freq, Integer interval, List<Integer> byDay,
            Integer count) {
    }

    /** 물질화 회차 뷰 — 정례(TIME 전용)라 date + HH:mm 시각 쌍. */
    public record OccurrenceInfo(LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    public record ProposalResponse(Long id, String title, String status, Slot slot,
            UserSummary organizer, List<ParticipantSummary> participants,
            RecurrenceInfo recurrence, int occurrenceCount,
            List<OccurrenceInfo> occurrences, String createdAt) {
    }

    public record ProposalSummary(Long id, String title, String status, Slot slot,
            UserSummary organizer, int acceptCount, int totalCount,
            RecurrenceInfo recurrence, int occurrenceCount, String createdAt) {
    }
}
