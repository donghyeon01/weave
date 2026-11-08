package app.weave.diary.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.DiaryPhoto;
import app.weave.diary.domain.DiaryPhotoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일기 사진 첨부 — 계약: 일기당 4장·장당 5MB·JPEG/PNG/WebP (매직바이트 판정). */
@Service
public class DiaryPhotoService {

    static final int MAX_PHOTOS = 4;
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final DiaryPhotoRepository photoRepository;
    private final DiaryPhotoStorage storage;

    public DiaryPhotoService(DiaryPhotoRepository photoRepository, DiaryPhotoStorage storage) {
        this.photoRepository = photoRepository;
        this.storage = storage;
    }

    @Transactional
    public DiaryPhoto upload(Long userId, Diary diary, byte[] bytes) {
        if (photoRepository.countByDiaryId(diary.getId()) >= MAX_PHOTOS) {
            throw new BusinessException(ErrorCode.DIARY_PHOTO_LIMIT);
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.DIARY_PHOTO_SIZE);
        }
        var detected = ImageSniffer.detect(bytes)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_PHOTO_TYPE));
        String path = storage.save(userId, diary.getId(), bytes, detected.extension());
        int nextOrder = photoRepository.findTopByDiaryIdOrderBySortOrderDesc(diary.getId())
                .map(p -> p.getSortOrder() + 1)
                .orElse(0);
        return photoRepository.save(new DiaryPhoto(diary.getId(), userId, path,
                detected.contentType(), bytes.length, nextOrder));
    }

    @Transactional(readOnly = true)
    public DiaryPhoto getOwned(Long userId, Diary diary, Long photoId) {
        return photoRepository.findByIdAndDiaryIdAndUserId(photoId, diary.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_PHOTO_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<DiaryPhoto> listOf(Long diaryId) {
        return photoRepository.findByDiaryIdOrderBySortOrderAsc(diaryId);
    }

    @Transactional(readOnly = true)
    public byte[] readBytes(DiaryPhoto photo) {
        return storage.read(photo.getStoragePath());
    }

    @Transactional
    public void delete(Long userId, Diary diary, Long photoId) {
        DiaryPhoto photo = getOwned(userId, diary, photoId);
        photoRepository.delete(photo);
        storage.delete(photo.getStoragePath());
    }
}
