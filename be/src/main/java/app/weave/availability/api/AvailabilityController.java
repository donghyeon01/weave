package app.weave.availability.api;

import app.weave.availability.domain.AvailabilityBlock;
import app.weave.availability.domain.BlockKind;
import app.weave.availability.domain.ScopeMode;
import app.weave.availability.service.AvailabilityService;
import app.weave.availability.service.AvailabilityService.BlockDraft;
import app.weave.availability.service.AvailabilityService.ScopeSummary;
import app.weave.availability.service.AvailabilityService.ScopeWithBlocks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @PostMapping("/scopes")
    public ResponseEntity<ScopeDetail> createScope(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateScopeRequest req) {
        ScopeWithBlocks result = service.createScope(userId, ScopeMode.valueOf(req.mode()),
                req.rangeStart(), req.rangeEnd());
        return ResponseEntity
                .created(URI.create("/api/availability/scopes/" + result.scope().getId()))
                .body(toDetail(result));
    }

    @GetMapping("/scopes")
    public List<ScopeSummaryResponse> listScopes(@AuthenticationPrincipal Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.listScopes(userId, from, to).stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/scopes/{id}")
    public ScopeDetail getScope(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return toDetail(service.getScope(userId, id));
    }

    @PutMapping("/scopes/{id}/blocks")
    public ScopeDetail putBlocks(@AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody PutBlocksRequest req) {
        List<BlockDraft> drafts = req.blocks().stream()
                .map(b -> new BlockDraft(BlockKind.valueOf(b.kind()), b.startAt(), b.endAt(),
                        b.blockDate()))
                .toList();
        return toDetail(service.putBlocks(userId, id, drafts, req.complete(),
                req.expectedRevision()));
    }

    @DeleteMapping("/scopes/{id}")
    public ResponseEntity<Void> deleteScope(@AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        service.deleteScope(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public PreferenceResponse getPreferences(@AuthenticationPrincipal Long userId) {
        return new PreferenceResponse(service.getUseCalendarInference(userId));
    }

    @PutMapping("/preferences")
    public PreferenceResponse putPreferences(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody PreferenceRequest req) {
        return new PreferenceResponse(
                service.putUseCalendarInference(userId, req.useCalendarInference()));
    }

    private ScopeDetail toDetail(ScopeWithBlocks r) {
        var s = r.scope();
        return new ScopeDetail(s.getId(), s.getMode().name(), s.getRangeStart().toString(),
                s.getRangeEnd().toString(), s.getStatus().name(), s.getRevision(),
                r.blocks().stream().map(this::toBlockResponse).toList(),
                fmt(s.getCreatedAt()), fmt(s.getUpdatedAt()));
    }

    private ScopeSummaryResponse toSummary(ScopeSummary r) {
        var s = r.scope();
        return new ScopeSummaryResponse(s.getId(), s.getMode().name(),
                s.getRangeStart().toString(), s.getRangeEnd().toString(), s.getStatus().name(),
                s.getRevision(), r.blockCount(), fmt(s.getCreatedAt()),
                fmt(s.getUpdatedAt()));
    }

    /** 계약 예시(§3.12)와 같이 초까지 표기 — LocalDateTime.toString은 :00초를 생략하므로 고정 패턴. */
    private static final java.time.format.DateTimeFormatter DATETIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static String fmt(java.time.LocalDateTime t) {
        return t == null ? null : DATETIME.format(t);
    }

    private BlockResponse toBlockResponse(AvailabilityBlock b) {
        return new BlockResponse(b.getKind().name(), fmt(b.getStartAt()), fmt(b.getEndAt()),
                b.getBlockDate() == null ? null : b.getBlockDate().toString());
    }

    public record CreateScopeRequest(
            @NotNull @Pattern(regexp = "TIME|DATE") String mode,
            @NotNull LocalDate rangeStart,
            @NotNull LocalDate rangeEnd) {
    }

    public record BlockRequest(
            @NotNull @Pattern(regexp = "TIME|DATE") String kind,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDate blockDate) {
    }

    /**
     * status는 "COMPLETED"만 허용 — 그 외 값·enum 외는 COMMON-VALIDATION (§3.12).
     * blocks는 필수 — 생략과 "전량 삭제 의도"를 구분한다 (의도된 삭제는 명시적 []).
     * null 원소는 컨테이너 제약으로 걸러 500 대신 400을 반환한다.
     */
    public record PutBlocksRequest(
            @NotNull List<@NotNull @Valid BlockRequest> blocks,
            @Pattern(regexp = "COMPLETED") String status,
            @NotNull Integer expectedRevision) {

        public boolean complete() {
            return "COMPLETED".equals(status);
        }
    }

    public record BlockResponse(String kind, String startAt, String endAt, String blockDate) {
    }

    public record ScopeDetail(Long id, String mode, String rangeStart, String rangeEnd,
            String status, int revision, List<BlockResponse> blocks, String createdAt,
            String updatedAt) {
    }

    public record ScopeSummaryResponse(Long id, String mode, String rangeStart, String rangeEnd,
            String status, int revision, long blockCount, String createdAt, String updatedAt) {
    }

    public record PreferenceRequest(@NotNull Boolean useCalendarInference) {
    }

    public record PreferenceResponse(boolean useCalendarInference) {
    }
}
