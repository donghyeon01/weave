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
    private String mood;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> tags;

    protected Diary() {
    }

    public Diary(Long userId, LocalDate date, String title, String content, String mood,
            List<String> tags) {
        this.userId = userId;
        this.date = date;
        this.title = title;
        this.content = content;
        this.mood = mood;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getMood() { return mood; }
    public List<String> getTags() { return tags; }

    public void update(String title, String content, String mood, List<String> tags) {
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
}
