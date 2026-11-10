package app.weave.common.port;

import java.time.LocalDate;
import java.util.List;

/** 공유 일기의 노출 계약 — mood는 Diary.Mood enum name 문자열, photos는 정렬 순 메타 목록. */
public record SharedDiary(Long id, Long userId, LocalDate date, String title, String content,
        String mood, List<String> tags, List<SharedPhotoRef> photos) {
}
