package app.weave.common.port;

/** 공유 일기 사진의 메타 — 바이트는 별도 엔드포인트(…/photos/{id})로 서빙한다. */
public record SharedPhotoRef(Long id, String contentType, Integer sizeBytes,
        Integer sortOrder) {
}
