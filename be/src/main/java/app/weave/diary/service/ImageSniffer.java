package app.weave.diary.service;

import java.util.Optional;

/**
 * 이미지 매직바이트 스니핑 — Content-Type 헤더는 위장 가능하므로 바이트 선두로 판정한다.
 * 허용: JPEG·PNG·WebP (계약 — 일기 사진 타입 제한).
 */
final class ImageSniffer {

    record Detected(String contentType, String extension) {
    }

    private ImageSniffer() {
    }

    static Optional<Detected> detect(byte[] head) {
        if (head.length >= 3
                && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(new Detected("image/jpeg", "jpg"));
        }
        if (head.length >= 4
                && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return Optional.of(new Detected("image/png", "png"));
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Optional.of(new Detected("image/webp", "webp"));
        }
        return Optional.empty();
    }
}
