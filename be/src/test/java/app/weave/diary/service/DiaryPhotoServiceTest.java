package app.weave.diary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.DiaryPhoto;
import app.weave.diary.domain.DiaryPhotoRepository;
import app.weave.user.service.UserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DiaryPhotoServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("weave.upload.dir", () -> uploadDir.toString());
    }

    @Autowired
    DiaryService diaryService;
    @Autowired
    DiaryPhotoService photoService;
    @Autowired
    DiaryPhotoRepository photoRepository;
    @Autowired
    UserService userService;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long otherId;
    private Diary diary;

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM diary_photos");
        jdbc.update("DELETE FROM diaries");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'", Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
        diary = diaryService.create(meId, LocalDate.of(2027, 5, 20), "제목", "본문", null, null, null);
    }

    @Test
    void 업로드는_행과_파일을_만든다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());

        assertThat(photo.getId()).isNotNull();
        assertThat(photo.getSortOrder()).isEqualTo(0);
        assertThat(photo.getContentType()).isEqualTo("image/jpeg");
        assertThat(Files.exists(uploadDir.resolve(photo.getStoragePath()))).isTrue();

        DiaryPhoto second = photoService.upload(meId, diary, jpeg());
        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(photoService.listOf(diary.getId())).hasSize(2);
    }

    @Test
    void 다섯_번째_사진은_LIMIT을_던진다() {
        for (int i = 0; i < 4; i++) {
            photoService.upload(meId, diary, jpeg());
        }
        assertThatThrownBy(() -> photoService.upload(meId, diary, jpeg()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIARY_PHOTO_LIMIT);
    }

    @Test
    void 이미지가_아닌_바이트는_TYPE을_던진다() {
        assertThatThrownBy(() -> photoService.upload(meId, diary, "not-an-image".getBytes()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIARY_PHOTO_TYPE);
    }

    @Test
    void 다섯MB_초과는_SIZE를_던진다() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> photoService.upload(meId, diary, big))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIARY_PHOTO_SIZE);
    }

    @Test
    void 타인_사진은_getOwned가_PHOTO_NOT_FOUND를_던진다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());
        assertThatThrownBy(() -> photoService.getOwned(otherId, diary, photo.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIARY_PHOTO_NOT_FOUND);
    }

    @Test
    void 삭제는_행과_파일을_지운다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());
        Path file = uploadDir.resolve(photo.getStoragePath());

        photoService.delete(meId, diary, photo.getId());

        assertThat(photoRepository.findById(photo.getId())).isEmpty();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void 일기_삭제는_첨부_디렉터리를_정리한다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());
        Path dir = uploadDir.resolve(photo.getStoragePath()).getParent();

        diaryService.delete(meId, diary.getDate());

        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    void 탈퇴는_사용자_사진_디렉터리를_정리한다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());
        Path userDir = uploadDir.resolve(photo.getStoragePath()).getParent().getParent();

        userService.deleteMe(meId);

        assertThat(Files.exists(userDir)).isFalse();
    }
}
