package app.weave.diary.service;

import app.weave.common.port.UserFileCleanup;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 일기 사진의 로컬 디스크 스토리지 — {uploadDir}/{userId}/{diaryId}/{uuid}.{ext} 레이아웃.
 * DB 행은 DiaryPhotoRepository가, 바이트는 이 컴포넌트가 담당한다 (분리 원천).
 */
@Component
public class DiaryPhotoStorage implements UserFileCleanup {

    private final Path root;

    public DiaryPhotoStorage(@Value("${weave.upload.dir:./uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 파일을 기록하고 DB에 저장할 상대 경로를 반환한다. */
    public String save(Long userId, Long diaryId, byte[] bytes, String extension) {
        Path dir = root.resolve(userId.toString()).resolve(diaryId.toString());
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("사진 저장에 실패했습니다.", e);
        }
        return userId + "/" + diaryId + "/" + name;
    }

    public byte[] read(String storagePath) {
        try {
            return Files.readAllBytes(resolveSafe(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("사진을 읽지 못했습니다.", e);
        }
    }

    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolveSafe(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("사진 삭제에 실패했습니다.", e);
        }
    }

    public void deleteForDiary(Long userId, Long diaryId) {
        deleteRecursively(root.resolve(userId.toString()).resolve(diaryId.toString()));
    }

    @Override
    public void deleteAllForUser(Long userId) {
        deleteRecursively(root.resolve(userId.toString()));
    }

    /** DB 경로도 신뢰하지 않는다 — root 밖으로 벗어나는 경로는 거부한다. */
    private Path resolveSafe(String storagePath) {
        Path path = root.resolve(storagePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("invalid storage path");
        }
        return path;
    }

    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("사진 디렉터리 삭제에 실패했습니다.", e);
        }
    }
}
