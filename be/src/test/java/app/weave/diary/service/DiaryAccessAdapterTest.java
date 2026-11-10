package app.weave.diary.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.weave.common.port.SharedDiary;
import app.weave.common.port.SharedPhotoBytes;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.Diary.Mood;
import app.weave.diary.domain.DiaryPhoto;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
class DiaryAccessAdapterTest {

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
    DiaryAccessAdapter adapter;
    @Autowired
    DiaryService diaryService;
    @Autowired
    DiaryPhotoService photoService;
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
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'",
                Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
        diary = diaryService.create(meId, LocalDate.of(2027, 5, 20), "제목", "본문",
                Mood.HAPPY, null, List.of("산책", "커피"));
    }

    @Test
    void 소유_일기는_SharedDiary로_변환된다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());

        Optional<SharedDiary> result = adapter.diaryOf(meId, diary.getId());

        assertThat(result).isPresent();
        SharedDiary shared = result.get();
        assertThat(shared.id()).isEqualTo(diary.getId());
        assertThat(shared.userId()).isEqualTo(meId);
        assertThat(shared.date()).isEqualTo(LocalDate.of(2027, 5, 20));
        assertThat(shared.title()).isEqualTo("제목");
        assertThat(shared.content()).isEqualTo("본문");
        assertThat(shared.mood()).isEqualTo("HAPPY");
        assertThat(shared.tags()).containsExactly("산책", "커피");
        assertThat(shared.photos()).hasSize(1);
        assertThat(shared.photos().get(0).id()).isEqualTo(photo.getId());
        assertThat(shared.photos().get(0).contentType()).isEqualTo("image/jpeg");
        assertThat(shared.photos().get(0).sortOrder()).isEqualTo(0);
    }

    @Test
    void 타인_소유_일기는_empty다() {
        assertThat(adapter.diaryOf(otherId, diary.getId())).isEmpty();
    }

    @Test
    void 미존재_일기는_empty다() {
        assertThat(adapter.diaryOf(meId, 999999L)).isEmpty();
    }

    @Test
    void 사진_바이트를_contentType과_함께_돌려준다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());

        Optional<SharedPhotoBytes> result = adapter.photoBytesOf(meId, diary.getId(),
                photo.getId());

        assertThat(result).isPresent();
        assertThat(result.get().contentType()).isEqualTo("image/jpeg");
        assertThat(result.get().bytes()).isEqualTo(jpeg());
    }

    @Test
    void 타인_사진은_empty다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());

        assertThat(adapter.photoBytesOf(otherId, diary.getId(), photo.getId())).isEmpty();
    }
}
