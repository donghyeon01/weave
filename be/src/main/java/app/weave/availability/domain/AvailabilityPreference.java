package app.weave.availability.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자별 가용성 선호 — 행이 없으면 기본값(useCalendarInference=false)으로 해석한다.
 * PK가 user_id라 별도 surrogate id 없음.
 */
@Entity
@Table(name = "availability_preferences")
public class AvailabilityPreference extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 완료 범위 밖의 근거 없는 구간을 "캘린더상 빈 시간"으로 추론할지 여부 (opt-in). */
    @Column(name = "use_calendar_inference", nullable = false)
    private boolean useCalendarInference = false;

    protected AvailabilityPreference() {
    }

    public AvailabilityPreference(Long userId, boolean useCalendarInference) {
        this.userId = userId;
        this.useCalendarInference = useCalendarInference;
    }

    public Long getUserId() { return userId; }
    public boolean isUseCalendarInference() { return useCalendarInference; }

    public void setUseCalendarInference(boolean useCalendarInference) {
        this.useCalendarInference = useCalendarInference;
    }
}
