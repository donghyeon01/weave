package app.weave.scheduling.domain;

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
@Table(name = "schedule_proposal_participants")
public class ScheduleProposalParticipant {

    public enum Response {
        PENDING, ACCEPTED, DECLINED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Response response;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** updated_at 없는 테이블 — created_at만 수동 초기화 (notifications와 같은 관례). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ScheduleProposalParticipant() {
    }

    public ScheduleProposalParticipant(Long proposalId, Long userId) {
        this.proposalId = proposalId;
        this.userId = userId;
        this.response = Response.PENDING;
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public Long getUserId() { return userId; }
    public Response getResponse() { return response; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void respond(Response r) {
        this.response = r;
        this.respondedAt = LocalDateTime.now();
    }
}
