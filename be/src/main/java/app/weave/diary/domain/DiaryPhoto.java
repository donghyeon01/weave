package app.weave.diary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 일기 첨부 사진 메타 — 바이트는 로컬 디스크({uploadDir}/{userId}/{diaryId}/)에,
 * 행은 소유권·정렬·서빙 정보의 원천. 불변 객체라 BaseTimeEntity를 쓰지 않는다.
 */
@Entity
@Table(name = "diary_photos")
public class DiaryPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diary_id", nullable = false)
    private Long diaryId;

    /** 소유권 검증·탈퇴 정리용 — diary JOIN 없이 user 기준으로 조회한다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DiaryPhoto() {
    }

    public DiaryPhoto(Long diaryId, Long userId, String storagePath, String contentType,
            Integer sizeBytes, Integer sortOrder) {
        this.diaryId = diaryId;
        this.userId = userId;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getDiaryId() { return diaryId; }
    public Long getUserId() { return userId; }
    public String getStoragePath() { return storagePath; }
    public String getContentType() { return contentType; }
    public Integer getSizeBytes() { return sizeBytes; }
    public Integer getSortOrder() { return sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
