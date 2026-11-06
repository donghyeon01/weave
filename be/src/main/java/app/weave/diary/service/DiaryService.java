package app.weave.diary.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.DiaryRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {

    private final DiaryRepository repository;

    public DiaryService(DiaryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Diary create(Long userId, LocalDate date, String title, String content, String mood,
            List<String> tags) {
        LocalDate target = date != null ? date : LocalDate.now();
        if (repository.findByUserIdAndDate(userId, target).isPresent()) {
            throw new BusinessException(ErrorCode.DIARY_DUPLICATE);
        }
        return repository.save(new Diary(userId, target, title, content, mood, tags));
    }

    @Transactional(readOnly = true)
    public Diary get(Long userId, LocalDate date) {
        return repository.findByUserIdAndDate(userId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Diary> listMonth(Long userId, YearMonth yearMonth) {
        LocalDate first = yearMonth.atDay(1);
        return repository.findByUserIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                userId, first, first.plusMonths(1));
    }

    @Transactional
    public Diary update(Long userId, LocalDate date, String title, String content, String mood,
            List<String> tags) {
        Diary diary = get(userId, date);
        diary.update(title, content, mood, tags);
        return diary;
    }

    @Transactional
    public void delete(Long userId, LocalDate date) {
        repository.delete(get(userId, date));
    }
}
