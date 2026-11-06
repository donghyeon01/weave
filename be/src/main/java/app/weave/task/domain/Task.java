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

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    protected Task() {
    }

    public Task(Long userId, String title, LocalDate dueDate, String idempotencyKey) {
        this.userId = userId;
        this.title = title;
        this.dueDate = dueDate;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public boolean isCompleted() { return completed; }
    public LocalDate getDueDate() { return dueDate; }

    public void update(String title, Boolean completed, LocalDate dueDate) {
        if (title != null) {
            this.title = title;
        }
        if (completed != null) {
            this.completed = completed;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
    }
}
