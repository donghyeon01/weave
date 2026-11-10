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
