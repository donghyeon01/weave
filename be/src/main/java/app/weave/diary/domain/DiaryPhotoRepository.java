package app.weave.diary.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryPhotoRepository extends JpaRepository<DiaryPhoto, Long> {

    List<DiaryPhoto> findByDiaryIdOrderBySortOrderAsc(Long diaryId);

    Optional<DiaryPhoto> findTopByDiaryIdOrderBySortOrderDesc(Long diaryId);

    /** 소유권 포함 조회 — 미존재·타인 소유 모두 empty로 묶어 404 처리 (IDOR 방지). */
    Optional<DiaryPhoto> findByIdAndDiaryIdAndUserId(Long id, Long diaryId, Long userId);

    List<DiaryPhoto> findByDiaryId(Long diaryId);

    long countByDiaryId(Long diaryId);
}
