package app.weave.availability.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자별 선호 시간대 — 요일 반복 패턴 조율의 preferenceScore 정렬 보조 데이터.
 * PUT 전량 교체(삭제+삽입)로만 바뀐다. user 연관은 두지 않고 user_id 컬럼만 갖는다
 * (블록·scope와 같은 독립 엔티티 패턴).
 */
@Entity
@Table(name = "availability_preferred_windows")
public class PreferredWindow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ISO 요일 — 1(월)~7(일). 컬럼은 TINYINT (V11). */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected PreferredWindow() {
    }

    public PreferredWindow(Long userId, int dayOfWeek, LocalTime startTime,
            LocalTime endTime) {
        this.userId = userId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
