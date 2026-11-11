package app.weave.diary.service;

import app.weave.common.port.DiaryAccess;
import app.weave.common.port.SharedDiary;
import app.weave.common.port.SharedPhotoBytes;
import app.weave.common.port.SharedPhotoRef;
import app.weave.diary.domain.DiaryPhotoRepository;
import app.weave.diary.domain.DiaryRepository;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** DiaryAccess 구현 — diary 데이터의 읽기 전용 출구 (capability-map 조회 포트 규약). */
@Component
public class DiaryAccessAdapter implements DiaryAccess {

    private final DiaryRepository diaryRepository;
    private final DiaryPhotoRepository photoRepository;
    private final DiaryPhotoStorage storage;

    public DiaryAccessAdapter(DiaryRepository diaryRepository,
            DiaryPhotoRepository photoRepository, DiaryPhotoStorage storage) {
        this.diaryRepository = diaryRepository;
        this.photoRepository = photoRepository;
        this.storage = storage;
    }

    @Override
    public Optional<SharedDiary> diaryOf(Long ownerId, Long diaryId) {
        return diaryRepository.findByIdAndUserId(diaryId, ownerId)
                .map(d -> new SharedDiary(d.getId(), ownerId, d.getDate(), d.getTitle(),
                        d.getContent(), d.getMood() != null ? d.getMood().name() : null,
                        d.getTags() != null ? d.getTags() : List.of(),
                        photoRepository.findByDiaryIdOrderBySortOrderAsc(d.getId()).stream()
                                .map(p -> new SharedPhotoRef(p.getId(), p.getContentType(),
                                        p.getSizeBytes(), p.getSortOrder()))
                                .toList()));
    }

    @Override
    public Optional<SharedPhotoBytes> photoBytesOf(Long ownerId, Long diaryId,
            Long photoId) {
        return photoRepository.findByIdAndDiaryIdAndUserId(photoId, diaryId, ownerId)
                .flatMap(p -> {
                    try {
                        return Optional.of(new SharedPhotoBytes(p.getContentType(),
                                storage.read(p.getStoragePath())));
                    } catch (UncheckedIOException e) {
                        // DB 행은 있으나 디스크 파일이 없는 경우 — 열람 불가는 부재와
                        // 동일하게 처리해 호출부가 404로 매핑하게 한다 (500 방지).
                        return Optional.empty();
                    }
                });
    }
}
