package app.weave.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 제안 회차의 물질화 스냅샷 — 생성 후 수정·삭제 없는 불변 행이라 ChatMessage와 같이
 * BaseTimeEntity 없이 created_at만 둔다 (테이블에 updated_at 없음, V11).
 * 단일 제안도 seq=0 한 행을 갖는다 — confirm의 회차별 이벤트 생성이 이 행들을 소비한다.
 */
@Entity
@Table(name = "schedule_proposal_occurrences")
public class ScheduleProposalOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ScheduleProposalOccurrence() {
    }

    public ScheduleProposalOccurrence(Long proposalId, int seq, LocalDateTime startTime,
            LocalDateTime endTime) {
        this.proposalId = proposalId;
        this.seq = seq;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public int getSeq() { return seq; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
