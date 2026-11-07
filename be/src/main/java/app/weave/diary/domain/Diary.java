package app.weave.diary.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diaries")
public class Diary extends BaseTimeEntity {

    /** 기분 라벨 — chk_diaries_mood 4종, 소문자 저장은 DiaryMoodConverter 참조. */
    public enum Mood {
        HAPPY, CALM, BUSY, TIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private Mood mood;

    /** 연결한 일정 — 소유권은 서비스가 EventAccess로 검증, null이면 미연결. */
    @Column(name = "event_id")
    private Long eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> tags;

    protected Diary() {
    }

    public Diary(Long userId, LocalDate date, String title, String content, Mood mood,
            Long eventId, List<String> tags) {
        this.userId = userId;
        this.date = date;
        this.title = title;
        this.content = content;
        this.mood = mood;
        this.eventId = eventId;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Mood getMood() { return mood; }
    public Long getEventId() { return eventId; }
    public List<String> getTags() { return tags; }

    public void update(String title, String content, Mood mood, List<String> tags) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (mood != null) {
            this.mood = mood;
        }
        if (tags != null) {
            this.tags = tags;
        }
    }

    /** 일정 연결·해제 — PATCH에서 필드 존재 시 호출, null이면 연결 해제. */
    public void linkEvent(Long eventId) {
        this.eventId = eventId;
    }
}
