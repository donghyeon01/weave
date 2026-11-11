package app.weave.availability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자가 "가능"으로 칠한 블록 — PUT 전량 교체(삭제+삽입)로만 바뀌는 불변 행이라
 * updated_at이 없어 BaseTimeEntity를 상속하지 않고 created_at만 직접 채운다.
 * scope 연관은 두지 않고 scope_id 컬럼만 갖는다 (블록은 서비스가 scope와 함께 로드).
 */
@Entity
@Table(name = "availability_blocks")
public class AvailabilityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private BlockKind kind;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "block_date")
    private LocalDate blockDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AvailabilityBlock() {
    }

    public static AvailabilityBlock time(Long scopeId, LocalDateTime startAt,
            LocalDateTime endAt) {
        AvailabilityBlock b = new AvailabilityBlock();
        b.scopeId = scopeId;
        b.kind = BlockKind.TIME;
        b.startAt = startAt;
        b.endAt = endAt;
        return b;
    }

    public static AvailabilityBlock date(Long scopeId, LocalDate blockDate) {
        AvailabilityBlock b = new AvailabilityBlock();
        b.scopeId = scopeId;
        b.kind = BlockKind.DATE;
        b.blockDate = blockDate;
        return b;
    }

    public Long getId() { return id; }
    public Long getScopeId() { return scopeId; }
    public BlockKind getKind() { return kind; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public LocalDate getBlockDate() { return blockDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
