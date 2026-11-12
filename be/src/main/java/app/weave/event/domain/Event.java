package app.weave.event.domain;

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
@Table(name = "events")
public class Event extends BaseTimeEntity {

    /** 생성 출처 — 캘린더 범례 "스마트 조율 약속" 구분 (manual=직접 등록, proposal=조율 확정). */
    public enum Source {
        MANUAL, PROPOSAL
    }

    /** 시간 점유 정책 — HARD 조율 불가(기본) / SOFT 조정 가능(conditional) / FREE 비점유(표시용). */
    public enum BusyPolicy {
        HARD, SOFT, FREE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 255)
    private String location;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    /** DB에는 소문자 저장 — EventSourceConverter 참조. */
    @Column(nullable = false, length = 10)
    private Source source = Source.MANUAL;

    /** source=PROPOSAL일 때 원 제안 — 참여 친구는 schedule_proposal_participants join으로 해결. */
    @Column(name = "proposal_id")
    private Long proposalId;

    /** source와 독립 — 출처가 아니라 시간 점유 의미 (spec §Schema). 대문자 enum 저장. */
    @Enumerated(EnumType.STRING)
    @Column(name = "busy_policy", nullable = false, length = 4)
    private BusyPolicy busyPolicy = BusyPolicy.HARD;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    protected Event() {
    }

    public Event(Long userId, String title, String description, LocalDateTime startTime,
            LocalDateTime endTime, String location, boolean allDay, Source source,
            Long proposalId, BusyPolicy busyPolicy, String idempotencyKey) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.allDay = allDay;
        this.source = source != null ? source : Source.MANUAL;
        this.proposalId = proposalId;
        this.busyPolicy = busyPolicy != null ? busyPolicy : BusyPolicy.HARD;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getLocation() { return location; }
    public boolean isAllDay() { return allDay; }
    public Source getSource() { return source; }
    public Long getProposalId() { return proposalId; }
    public BusyPolicy getBusyPolicy() { return busyPolicy; }

    public void update(String title, String description, LocalDateTime startTime,
            LocalDateTime endTime, String location, Boolean allDay,
            BusyPolicy busyPolicy) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startTime != null) {
            this.startTime = startTime;
        }
        if (endTime != null) {
            this.endTime = endTime;
        }
        if (location != null) {
            this.location = location;
        }
        if (allDay != null) {
            this.allDay = allDay;
        }
        if (busyPolicy != null) {
            this.busyPolicy = busyPolicy;
        }
    }
}
