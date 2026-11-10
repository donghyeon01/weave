package app.weave.common.port;

/** 사진 서빙 응답 — contentType + 바이트. */
public record SharedPhotoBytes(String contentType, byte[] bytes) {
}
