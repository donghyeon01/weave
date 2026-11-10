package app.weave.scheduling.api;

import app.weave.scheduling.domain.ScheduleProposalParticipant;
import app.weave.scheduling.domain.ScheduleProposalParticipant.Response;
import app.weave.scheduling.service.ProposalService;
import app.weave.scheduling.service.ProposalService.ProposalDetail;
import app.weave.scheduling.service.Slot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
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
        ProposalDetail detail = service.create(me, req.title(), req.slot(), req.participantIds(),
                idempotencyKey);
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
                p.getCreatedAt().toString());
    }

    private ProposalSummary toSummary(ProposalDetail d) {
        var p = d.proposal();
        long acceptCount = d.participants().stream()
                .filter(pp -> pp.getResponse() == Response.ACCEPTED).count();
        return new ProposalSummary(p.getId(), p.getTitle(), p.getStatus().name(), p.toSlot(),
                new UserSummary(d.organizer().getId(), d.organizer().getNickname()),
                (int) acceptCount, d.participants().size(), p.getCreatedAt().toString());
    }

    public record CreateRequest(@NotBlank @Size(max = 200) String title, @NotNull Slot slot,
            @NotEmpty List<Long> participantIds) {
    }

    public record RespondRequest(@NotNull ScheduleProposalParticipant.Response response) {
    }

    public record UserSummary(Long id, String nickname) {
    }

    public record ParticipantSummary(UserSummary user, String response, String respondedAt) {
    }

    public record ProposalResponse(Long id, String title, String status, Slot slot,
            UserSummary organizer, List<ParticipantSummary> participants, String createdAt) {
    }

    public record ProposalSummary(Long id, String title, String status, Slot slot,
            UserSummary organizer, int acceptCount, int totalCount, String createdAt) {
    }
}
