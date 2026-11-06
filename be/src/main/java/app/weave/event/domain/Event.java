package app.weave.event.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    protected Event() {
    }

    public Event(Long userId, String title, String description, LocalDateTime startTime,
            LocalDateTime endTime, String location, boolean allDay, Source source,
            String idempotencyKey) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.allDay = allDay;
        this.source = source != null ? source : Source.MANUAL;
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

    public void update(String title, String description, LocalDateTime startTime,
            LocalDateTime endTime, String location, Boolean allDay) {
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
    }
}
