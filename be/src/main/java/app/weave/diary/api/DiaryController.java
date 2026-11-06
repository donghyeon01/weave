package app.weave.diary.api;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.diary.domain.Diary;
import app.weave.diary.service.DiaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRequest req) {
        Diary diary = service.create(userId, req.date(), req.title(), req.content(), req.mood(),
                req.tags());
        return ResponseEntity.created(URI.create("/api/diaries/" + diary.getDate()))
                .body(toResponse(diary));
    }

    @GetMapping
    public List<DiarySummary> listMonth(@AuthenticationPrincipal Long userId,
            @RequestParam String yearMonth) {
        YearMonth ym = parseYearMonth(yearMonth);
        return service.listMonth(userId, ym).stream()
                .map(d -> new DiarySummary(d.getDate().toString(), d.getTitle()))
                .toList();
    }

    @GetMapping("/{date}")
    public DiaryResponse get(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return toResponse(service.get(userId, date));
    }

    @PatchMapping("/{date}")
    public DiaryResponse update(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody UpdateRequest req) {
        return toResponse(
                service.update(userId, date, req.title(), req.content(), req.mood(), req.tags()));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(userId, date);
        return ResponseEntity.noContent().build();
    }

    private YearMonth parseYearMonth(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
    }

    private DiaryResponse toResponse(Diary d) {
        return new DiaryResponse(d.getId(), d.getDate().toString(), d.getTitle(), d.getContent(),
                d.getMood(), d.getTags(), d.getCreatedAt().toString(), d.getUpdatedAt().toString());
    }

    public record CreateRequest(LocalDate date, @Size(max = 100) String title,
            @NotBlank String content, @Size(max = 20) String mood, List<String> tags) {
    }

    public record UpdateRequest(@Size(max = 100) String title, String content,
            @Size(max = 20) String mood, List<String> tags) {
    }

    public record DiarySummary(String date, String title) {
    }

    public record DiaryResponse(Long id, String date, String title, String content, String mood,
            List<String> tags, String createdAt, String updatedAt) {
    }
}
