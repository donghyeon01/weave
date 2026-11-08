package app.weave.diary.api;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.DiaryPhoto;
import app.weave.diary.service.DiaryPhotoService;
import app.weave.diary.service.DiaryService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService service;
    private final DiaryPhotoService photoService;

    public DiaryController(DiaryService service, DiaryPhotoService photoService) {
        this.service = service;
        this.photoService = photoService;
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRequest req) {
        Diary diary = service.create(userId, req.date(), req.title(), req.content(),
                parseMood(req.mood()), req.eventId(), req.tags());
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
            @Valid @RequestBody UpdateRequest req) {
        return toResponse(service.update(userId, date, req.getTitle(), req.getContent(),
                parseMood(req.getMood()), req.isMoodSet(), req.getTags(), req.isEventIdSet(),
                req.getEventId()));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(userId, date);
        return ResponseEntity.noContent().build();
    }

    /** 사진 업로드 — multipart `file`. 일기가 먼저 존재해야 한다 (없으면 DIARY-NOT-FOUND). */
    @PostMapping("/{date}/photos")
    public ResponseEntity<PhotoResponse> uploadPhoto(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("file") MultipartFile file) throws IOException {
        DiaryPhoto photo = photoService.upload(userId, service.get(userId, date), file.getBytes());
        return ResponseEntity
                .created(URI.create("/api/diaries/" + date + "/photos/" + photo.getId()))
                .body(toPhotoResponse(photo));
    }

    /** 사진 서빙 — <img>가 Authorization을 못 보내므로 fe는 인증 fetch→objectURL로 표시한다. */
    @GetMapping("/{date}/photos/{photoId}")
    public ResponseEntity<byte[]> getPhoto(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable Long photoId) {
        DiaryPhoto photo = photoService.getOwned(userId, service.get(userId, date), photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .body(photoService.readBytes(photo));
    }

    @DeleteMapping("/{date}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable Long photoId) {
        photoService.delete(userId, service.get(userId, date), photoId);
        return ResponseEntity.noContent().build();
    }

    private YearMonth parseYearMonth(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
    }

    /** @Pattern이 통과한 값만 들어온다 — 소문자 계약 값을 enum으로 변환. */
    private static Diary.Mood parseMood(String mood) {
        return mood == null ? null : Diary.Mood.valueOf(mood.toUpperCase());
    }

    private DiaryResponse toResponse(Diary d) {
        List<PhotoResponse> photos = photoService.listOf(d.getId()).stream()
                .map(this::toPhotoResponse)
                .toList();
        return new DiaryResponse(d.getId(), d.getDate().toString(), d.getTitle(), d.getContent(),
                d.getMood() == null ? null : d.getMood().name().toLowerCase(), d.getTags(),
                d.getEventId(), photos, d.getCreatedAt().toString(), d.getUpdatedAt().toString());
    }

    private PhotoResponse toPhotoResponse(DiaryPhoto p) {
        return new PhotoResponse(p.getId(), p.getContentType(), p.getSizeBytes(), p.getSortOrder(),
                p.getCreatedAt().toString());
    }

    public record CreateRequest(LocalDate date, @Size(max = 100) String title,
            @NotBlank String content,
            @Pattern(regexp = "happy|calm|busy|tired") String mood, List<String> tags,
            Long eventId) {
    }

    /**
     * eventId·mood는 필드 존재 자체가 의미 — 명시적 null은 해제(계약 §3.5)라
     * "필드 없음(유지)"과 구분해야 해서 setter 플래그를 둔 클래스로 선언한다.
     */
    public static final class UpdateRequest {
        @Size(max = 100)
        private String title;
        private String content;
        @Pattern(regexp = "happy|calm|busy|tired")
        private String mood;
        private List<String> tags;
        private Long eventId;
        private boolean eventIdSet;
        private boolean moodSet;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getMood() { return mood; }
        public boolean isMoodSet() { return moodSet; }

        @JsonProperty("mood")
        public void setMood(String mood) {
            this.mood = mood;
            this.moodSet = true;
        }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Long getEventId() { return eventId; }
        public boolean isEventIdSet() { return eventIdSet; }

        @JsonProperty("eventId")
        public void setEventId(Long eventId) {
            this.eventId = eventId;
            this.eventIdSet = true;
        }
    }

    public record DiarySummary(String date, String title) {
    }

    public record DiaryResponse(Long id, String date, String title, String content, String mood,
            List<String> tags, Long eventId, List<PhotoResponse> photos, String createdAt,
            String updatedAt) {
    }

    /** URL 없음 — 바이트는 인증이 필요한 GET .../photos/{id}로만 내린다. */
    public record PhotoResponse(Long id, String contentType, Integer sizeBytes, Integer sortOrder,
            String createdAt) {
    }
}
