package app.weave.scheduling.api;

import app.weave.scheduling.domain.Mode;
import app.weave.scheduling.service.ScheduleRecommender.Candidate;
import app.weave.scheduling.service.SchedulingService;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduling")
public class SchedulingController {

    private final SchedulingService service;

    public SchedulingController(SchedulingService service) {
        this.service = service;
    }

    /** 조회성 계산 POST — 리소스를 만들지 않는다 (계약 §3.8). */
    @PostMapping
    public CandidatesResponse recommend(@AuthenticationPrincipal Long me,
            @Valid @RequestBody RecommendRequest req) {
        List<Candidate> candidates = service.recommend(me, req.mode(), req.rangeStart(),
                req.rangeEnd(), req.participantIds(), req.windowStart(), req.windowEnd(),
                req.windows() == null ? null
                        : req.windows().stream()
                                // null 원소는 [null,null]로 통과시켜 service 계층의 400 검증으로 위임
                                .map(w -> w == null ? new LocalTime[]{null, null}
                                        : new LocalTime[]{w.startTime(), w.endTime()})
                                .toList(),
                req.stepMinutes(), req.slotMinutes(), req.durationDays());
        return new CandidatesResponse(candidates);
    }

    public record RecommendRequest(@NotNull Mode mode, @NotNull LocalDate rangeStart,
            @NotNull LocalDate rangeEnd, List<Long> participantIds,
            @JsonFormat(pattern = "HH:mm") LocalTime windowStart,
            @JsonFormat(pattern = "HH:mm") LocalTime windowEnd,
            List<TimeWindow> windows, Integer stepMinutes,
            Integer slotMinutes, Integer durationDays) {
    }

    /** 다중 탐색 창 — windows가 있으면 windowStart/End보다 우선한다 (계약 §3.8). */
    public record TimeWindow(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    public record CandidatesResponse(List<Candidate> candidates) {
    }
}
