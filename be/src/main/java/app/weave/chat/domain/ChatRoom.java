package app.weave.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 제안 범위 단체채팅 방. proposal_id는 schedule_proposals의 논리 참조(FK 없음)이며
 * title·mode·start/end_time은 생성 시점 스냅샷 — 방 목록을 scheduling 조회 없이 서빙한다.
 * chat_rooms는 updated_at이 없어 BaseTimeEntity를 상속하지 않는다 (notifications 관례).
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    public enum Status {
        OPEN, CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Mode mode;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected ChatRoom() {
    }

    public ChatRoom(Long proposalId, String title, Mode mode, LocalDateTime startTime,
            LocalDateTime endTime) {
        this.proposalId = proposalId;
        this.title = title;
        this.mode = mode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = Status.OPEN;
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public String getTitle() { return title; }
    public Mode getMode() { return mode; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }

    /** 멱등 — 이미 CLOSED면 재호출은 무시한다 (종결 전이 중복 호출 허용). */
    public void close() {
        if (this.status == Status.CLOSED) {
            return;
        }
        this.status = Status.CLOSED;
        this.closedAt = LocalDateTime.now();
    }
}
