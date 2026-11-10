package app.weave.share.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.SharedPhotoBytes;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.Diary.Mood;
import app.weave.diary.domain.DiaryPhoto;
import app.weave.diary.service.DiaryPhotoService;
import app.weave.diary.service.DiaryService;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.domain.ShareRepository;
import app.weave.share.domain.ShareTargetRepository;
import app.weave.share.service.ShareService.ReceivedShare;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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
class ShareServiceTest {

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
    ShareService shareService;
    @Autowired
    ShareRepository shareRepository;
    @Autowired
    ShareTargetRepository targetRepository;
    @Autowired
    DiaryService diaryService;
    @Autowired
    DiaryPhotoService photoService;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long f1Id;
    private Long f2Id;
    private Long outsiderId;
    private Diary diary;

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM share_replies");
        jdbc.update("DELETE FROM share_targets");
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM diary_photos");
        jdbc.update("DELETE FROM diaries");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('f1@weave.local','일','일','demo'),"
                + "('f2@weave.local','이','이','demo'),('out@weave.local','외','외','demo')");
        meId = idOf("me@weave.local");
        f1Id = idOf("f1@weave.local");
        f2Id = idOf("f2@weave.local");
        outsiderId = idOf("out@weave.local");
        befriend(meId, f1Id);
        befriend(meId, f2Id);
        diary = diaryService.create(meId, LocalDate.of(2027, 5, 20), "제주 여행", "본문",
                Mood.HAPPY, null, List.of("제주"));
    }

    private Long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void befriend(Long a, Long b) {
        jdbc.update("INSERT INTO friendships(user_id,friend_id,status) VALUES (?,?,'ACCEPTED')",
                a, b);
    }

    private void assertError(ErrorCode code, Runnable r) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    private int notificationCount(Long userId, String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id=? AND type=?",
                Integer.class, userId, type);
    }

    @Test
    void 생성하면_대상행과_알림이_저장된다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id, f2Id));

        assertThat(shareRepository.findById(share.getId())).isPresent();
        assertThat(targetRepository.findByShareId(share.getId())).hasSize(2);
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);
        assertThat(notificationCount(f2Id, "SHARED_ITEM")).isEqualTo(1);
        assertThat(notificationCount(meId, "SHARED_ITEM")).isEqualTo(0);
    }

    @Test
    void 타인_항목과_미존재_항목은_404() {
        Diary others = diaryService.create(f1Id, LocalDate.of(2027, 5, 21), "남의 일기",
                "본문", null, null, null);

        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.create(meId, ShareItemType.DIARY, others.getId(),
                        List.of(f1Id)));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.create(meId, ShareItemType.DIARY, 999999L, List.of(f1Id)));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.create(meId, ShareItemType.EVENT, 999999L, List.of(f1Id)));
    }

    @Test
    void 비친구_본인_빈_대상은_400() {
        assertError(ErrorCode.SHARE_TARGET_INVALID,
                () -> shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                        List.of(outsiderId)));
        assertError(ErrorCode.SHARE_TARGET_INVALID,
                () -> shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                        List.of(f1Id, outsiderId)));
        assertError(ErrorCode.SHARE_TARGET_INVALID,
                () -> shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                        List.of(meId)));
        assertError(ErrorCode.SHARE_TARGET_INVALID,
                () -> shareService.create(meId, ShareItemType.DIARY, diary.getId(), List.of()));
        assertError(ErrorCode.SHARE_TARGET_INVALID,
                () -> shareService.create(meId, ShareItemType.DIARY, diary.getId(), null));
        assertThat(shareRepository.count()).isZero();
    }

    @Test
    void 같은_항목_재공유는_409() {
        shareService.create(meId, ShareItemType.DIARY, diary.getId(), List.of(f1Id));

        assertError(ErrorCode.SHARE_ALREADY_EXISTS,
                () -> shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                        List.of(f2Id)));
    }

    @Test
    void received_피드는_대상에게만_노출된다() {
        Share s1 = shareService.create(meId, ShareItemType.DIARY, diary.getId(), List.of(f1Id));
        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) "
                + "VALUES (?,'캡스톤 회의','2027-06-01 10:00:00','2027-06-01 11:00:00')", meId);
        Long eventId = jdbc.queryForObject("SELECT id FROM events LIMIT 1", Long.class);
        Share s2 = shareService.create(meId, ShareItemType.EVENT, eventId, List.of(f1Id));

        List<ReceivedShare> feed = shareService.received(f1Id, null, 20);
        assertThat(feed).extracting(ReceivedShare::shareId)
                .containsExactly(s2.getId(), s1.getId()); // share.id desc
        assertThat(feed.get(0).itemType()).isEqualTo(ShareItemType.EVENT);
        assertThat(feed.get(0).owner().nickname()).isEqualTo("나");
        assertThat(feed.get(1).itemTitle()).isEqualTo("제주 여행");

        // 커서 경계 — id < cursor
        List<ReceivedShare> second = shareService.received(f1Id, s2.getId(), 20);
        assertThat(second).extracting(ReceivedShare::shareId).containsExactly(s1.getId());

        // 비대상 사용자는 비어있다
        assertThat(shareService.received(outsiderId, null, 20)).isEmpty();
        assertThat(shareService.received(f2Id, null, 20)).isEmpty();

        assertError(ErrorCode.COMMON_VALIDATION,
                () -> shareService.received(f1Id, null, 0));
        assertError(ErrorCode.COMMON_VALIDATION,
                () -> shareService.received(f1Id, null, 51));
    }

    @Test
    void detail은_소유자와_유효대상만_조회하고_대상목록은_소유자에게만_보인다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));

        var ownerView = shareService.detail(meId, share.getId());
        assertThat(ownerView.targetUserIds()).containsExactly(f1Id);
        assertThat(ownerView.item()).isInstanceOf(app.weave.common.port.SharedDiary.class);

        var targetView = shareService.detail(f1Id, share.getId());
        assertThat(targetView.targetUserIds()).isNull();

        assertError(ErrorCode.SHARE_NOT_AUDIENCE,
                () -> shareService.detail(outsiderId, share.getId()));
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(meId, 999999L));
    }

    @Test
    void 친구해제후_detail은_404고_대상행이_지연정리되어_재친구해도_부활하지않는다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));
        jdbc.update("DELETE FROM friendships WHERE user_id=? AND friend_id=?", meId, f1Id);

        assertError(ErrorCode.SHARE_NOT_AUDIENCE,
                () -> shareService.detail(f1Id, share.getId()));
        assertThat(targetRepository.findByShareId(share.getId())).isEmpty();

        // 재친구 — 대상 행이 지연 정리됐으므로 공유는 부활하지 않는다
        befriend(meId, f1Id);
        assertError(ErrorCode.SHARE_NOT_AUDIENCE,
                () -> shareService.detail(f1Id, share.getId()));
        assertThat(shareService.received(f1Id, null, 20)).isEmpty();
    }

    @Test
    void 친구해제후_received가_지연정리하고_소유자목록도_대상을_정리한다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id, f2Id));
        jdbc.update("DELETE FROM friendships WHERE user_id=? AND friend_id=?", meId, f1Id);

        assertThat(shareService.received(f1Id, null, 20)).isEmpty();
        assertThat(targetRepository.findByShareIdAndUserId(share.getId(), f1Id)).isEmpty();
        // 유효 대상 f2는 남는다
        assertThat(targetRepository.findByShareIdAndUserId(share.getId(), f2Id)).isPresent();
        assertThat(shareService.myShares(meId).get(0).targetUserIds())
                .containsExactly(f2Id);
    }

    @Test
    void 원본삭제후_피드와_목록과_상세에서_비노출이다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));
        jdbc.update("DELETE FROM diaries WHERE id=?", diary.getId());

        assertThat(shareService.received(f1Id, null, 20)).isEmpty();
        assertThat(shareService.myShares(meId)).isEmpty();
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.detail(f1Id, share.getId()));
        // share 행 자체는 남는다 — 원본 재생성 시 같은 itemId로 재노출되는 정책은 아님
        assertThat(shareRepository.findById(share.getId())).isPresent();
    }

    @Test
    void editTargets_add는_알림을_보내고_remove는_즉시_비가시다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));
        int before = notificationCount(f2Id, "SHARED_ITEM");

        shareService.editTargets(meId, share.getId(), List.of(f2Id), null);
        assertThat(notificationCount(f2Id, "SHARED_ITEM")).isEqualTo(before + 1);
        assertThat(shareService.detail(f2Id, share.getId()).item()).isNotNull();

        // 이미 대상인 사용자를 add해도 알림은 중복 발행되지 않는다
        shareService.editTargets(meId, share.getId(), List.of(f1Id), null);
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);

        // 비친구 add는 400
        assertError(ErrorCode.SHARE_TARGET_INVALID,
                () -> shareService.editTargets(meId, share.getId(), List.of(outsiderId), null));

        shareService.editTargets(meId, share.getId(), null, List.of(f1Id));
        assertError(ErrorCode.SHARE_NOT_AUDIENCE,
                () -> shareService.detail(f1Id, share.getId()));
        assertThat(shareService.detail(meId, share.getId()).targetUserIds())
                .containsExactly(f2Id);
    }

    @Test
    void editTargets와_delete는_비소유자에게_404다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));

        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.editTargets(f1Id, share.getId(), List.of(f2Id), null));
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.delete(f1Id, share.getId()));
    }

    @Test
    void 공유해제는_targets와_replies를_연쇄삭제한다() {
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));
        jdbc.update("INSERT INTO share_replies(share_id,author_id,content) VALUES (?,?, '답글')",
                share.getId(), f1Id);

        shareService.delete(meId, share.getId());

        assertThat(shareRepository.findById(share.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_targets WHERE share_id=?",
                Integer.class, share.getId())).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM share_replies WHERE share_id=?",
                Integer.class, share.getId())).isEqualTo(0);
    }

    @Test
    void 사진은_열람자에게만_서빙되고_EVENT공유는_404다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());
        Share share = shareService.create(meId, ShareItemType.DIARY, diary.getId(),
                List.of(f1Id));

        SharedPhotoBytes bytes = shareService.photo(f1Id, share.getId(), photo.getId());
        assertThat(bytes.contentType()).isEqualTo("image/jpeg");
        assertThat(bytes.bytes()).isEqualTo(jpeg());
        assertThat(shareService.photo(meId, share.getId(), photo.getId()).bytes())
                .isEqualTo(jpeg());

        assertError(ErrorCode.SHARE_NOT_AUDIENCE,
                () -> shareService.photo(outsiderId, share.getId(), photo.getId()));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.photo(f1Id, share.getId(), 999999L));

        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) "
                + "VALUES (?,'회의','2027-06-01 10:00:00','2027-06-01 11:00:00')", meId);
        Long eventId = jdbc.queryForObject("SELECT id FROM events LIMIT 1", Long.class);
        Share eventShare = shareService.create(meId, ShareItemType.EVENT, eventId,
                List.of(f1Id));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.photo(f1Id, eventShare.getId(), photo.getId()));
    }
}
