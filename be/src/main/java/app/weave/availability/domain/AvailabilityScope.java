package app.weave.availability.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 가용성 입력 범위 — 사용자가 "가능한 시간"을 칠할 기간 단위.
 * revision은 블록 PUT의 낙관 동시성 키 — 매 성공 PUT마다 +1.
 */
@Entity
@Table(name = "availability_scopes")
public class AvailabilityScope extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private ScopeMode mode;

    @Column(name = "range_start", nullable = false)
    private LocalDate rangeStart;

    @Column(name = "range_end", nullable = false)
    private LocalDate rangeEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private ScopeStatus status = ScopeStatus.INPUT;

    @Column(nullable = false)
    private int revision = 0;

    protected AvailabilityScope() {
    }

    public AvailabilityScope(Long userId, ScopeMode mode, LocalDate rangeStart,
            LocalDate rangeEnd) {
        this.userId = userId;
        this.mode = mode;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public ScopeMode getMode() { return mode; }
    public LocalDate getRangeStart() { return rangeStart; }
    public LocalDate getRangeEnd() { return rangeEnd; }
    public ScopeStatus getStatus() { return status; }
    public int getRevision() { return revision; }

    /** INPUT→COMPLETED만 전이 — 이미 COMPLETED면 유지 (재PUT 허용). */
    public void complete() {
        if (status == ScopeStatus.INPUT) {
            status = ScopeStatus.COMPLETED;
        }
    }

    public void bumpRevision() {
        this.revision++;
    }

    /** 블록이 들어갈 수 있는 범위 — [rangeStart 00:00, (rangeEnd+1일) 00:00) 반개방. */
    public boolean covers(java.time.LocalDateTime at) {
        return !at.isBefore(rangeStart.atStartOfDay())
                && at.isBefore(rangeEnd.plusDays(1).atStartOfDay());
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(rangeStart) && !date.isAfter(rangeEnd);
    }
}
