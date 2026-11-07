package app.weave.task.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class Task extends BaseTimeEntity {

    /** 우선순위 — DB에는 소문자 저장 (chk_tasks_priority), TaskPriorityConverter 참조. */
    public enum Priority {
        URGENT, HIGH, NORMAL, LOW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Column(length = 200)
    private String memo;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    protected Task() {
    }

    public Task(Long userId, String title, LocalDate dueDate, Priority priority, String memo,
            String idempotencyKey) {
        this.userId = userId;
        this.title = title;
        this.dueDate = dueDate;
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.memo = memo;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public boolean isCompleted() { return completed; }
    public LocalDate getDueDate() { return dueDate; }
    public Priority getPriority() { return priority; }
    public String getMemo() { return memo; }

    public void update(String title, Boolean completed, LocalDate dueDate, Priority priority,
            String memo) {
        if (title != null) {
            this.title = title;
        }
        if (completed != null) {
            this.completed = completed;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
