package app.weave.scheduling.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule_proposals")
public class ScheduleProposal extends BaseTimeEntity {

    public enum Status {
        PENDING, ALL_ACCEPTED, CONFIRMED, DECLINED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Mode mode;

    /** DATE 모드는 endDate+1일 자정의 배타 상한으로 저장된다 (db-schema §5 저장 규약). */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    // 정례 4컬럼은 CHECK 제약상 all-or-nothing — applyRecurrence로만 채운다 (V11)
    @Column(name = "recurrence_freq", length = 8)
    private String recurrenceFreq;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval;

    /** ISO 요일 CSV("2,4") — 파싱은 {@link #byDays()}. */
    @Column(name = "recurrence_by_days", length = 13)
    private String recurrenceByDays;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    protected ScheduleProposal() {
    }

    public ScheduleProposal(Long organizerId, String title, Mode mode, LocalDateTime startTime,
            LocalDateTime endTime, String idempotencyKey) {
        this.organizerId = organizerId;
        this.title = title;
        this.mode = mode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.idempotencyKey = idempotencyKey;
        this.status = Status.PENDING;
    }

    public Long getId() { return id; }
    public Long getOrganizerId() { return organizerId; }
    public String getTitle() { return title; }
    public Mode getMode() { return mode; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Status getStatus() { return status; }
    public String getRecurrenceFreq() { return recurrenceFreq; }
    public Integer getRecurrenceInterval() { return recurrenceInterval; }
    public String getRecurrenceByDays() { return recurrenceByDays; }
    public Integer getOccurrenceCount() { return occurrenceCount; }

    /** 정례 메타 기록 — 4컬럼을 한 번에 채워 CHECK의 all-or-nothing을 지킨다. */
    public void applyRecurrence(String freq, int interval, Set<Integer> byDays, int count) {
        this.recurrenceFreq = freq;
        this.recurrenceInterval = interval;
        this.recurrenceByDays = byDays.stream().sorted().map(String::valueOf)
                .collect(Collectors.joining(","));
        this.occurrenceCount = count;
    }

    /** recurrence_by_days CSV → ISO 요일(1=월~7=일) 집합 — 비정례는 빈 집합. */
    public Set<Integer> byDays() {
        if (recurrenceByDays == null || recurrenceByDays.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(recurrenceByDays.split(",")).map(String::trim)
                .map(Integer::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    /** 행→API Slot 복원 — DATE는 저장된 배타 상한에서 1일을 빼 포함 끝으로 돌린다. */
    public app.weave.scheduling.service.Slot toSlot() {
        if (mode == Mode.TIME) {
            return new app.weave.scheduling.service.Slot.Time(
                    startTime.toLocalDate(), startTime.toLocalTime(), endTime.toLocalTime());
        }
        return new app.weave.scheduling.service.Slot.Days(
                startTime.toLocalDate(), endTime.toLocalDate().minusDays(1));
    }

    public void markAllAccepted() { this.status = Status.ALL_ACCEPTED; }
    public void confirm() { this.status = Status.CONFIRMED; }
    public void decline() { this.status = Status.DECLINED; }
    public void cancel() { this.status = Status.CANCELLED; }
}
