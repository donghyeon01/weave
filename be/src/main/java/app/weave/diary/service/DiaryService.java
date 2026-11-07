package app.weave.diary.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.EventAccess;
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
    private final EventAccess eventAccess;

    public DiaryService(DiaryRepository repository, EventAccess eventAccess) {
        this.repository = repository;
        this.eventAccess = eventAccess;
    }

    @Transactional
    public Diary create(Long userId, LocalDate date, String title, String content, Diary.Mood mood,
            Long eventId, List<String> tags) {
        LocalDate target = date != null ? date : LocalDate.now();
        if (repository.findByUserIdAndDate(userId, target).isPresent()) {
            throw new BusinessException(ErrorCode.DIARY_DUPLICATE);
        }
        validateEventLink(userId, eventId);
        return repository.save(new Diary(userId, target, title, content, mood, eventId, tags));
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

    /**
     * 부분 수정 — eventIdSet=true이면 eventId로 덮어쓴다 (null은 연결 해제, 계약 §3.5).
     * 나머지 필드는 null이면 유지.
     */
    @Transactional
    public Diary update(Long userId, LocalDate date, String title, String content, Diary.Mood mood,
            List<String> tags, boolean eventIdSet, Long eventId) {
        Diary diary = get(userId, date);
        diary.update(title, content, mood, tags);
        if (eventIdSet) {
            validateEventLink(userId, eventId);
            diary.linkEvent(eventId);
        }
        return diary;
    }

    @Transactional
    public void delete(Long userId, LocalDate date) {
        repository.delete(get(userId, date));
    }

    /** 연결 일정은 본인 소유만 — 미존재·타인 소유는 400 (계약 §3.5). eventId null은 미연결이라 통과. */
    private void validateEventLink(Long userId, Long eventId) {
        if (eventId != null && !eventAccess.isOwnedBy(userId, eventId)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
    }
}
