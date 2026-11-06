package app.weave.diary.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDate(Long userId, LocalDate date);

    // 월 목록: Between은 양끝 포함이라 말일 다음날까지 잡히므로 LessThan 배타 상한 사용
    List<Diary> findByUserIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
            Long userId, LocalDate first, LocalDate nextMonthFirst);
}
