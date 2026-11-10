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
import app.weave.friend.service.FriendGroupService;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.domain.ShareRepository;
import app.weave.share.domain.ShareVisibility;
import app.weave.share.service.ShareService.FeedItem;
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
    FriendGroupService groupService;
    @Autowired
    ShareRepository shareRepository;
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
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM feed_subscriptions");
        jdbc.update("DELETE FROM friend_group_members");
        jdbc.update("DELETE FROM friend_groups");
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

    private Share upsertFriends(Long itemId) {
        return shareService.upsert(meId, ShareItemType.DIARY, itemId,
                ShareVisibility.FRIENDS, null);
    }

    @Test
    void upsert는_항목당_1행만_두고_범위를_전환한다() {
        Share s1 = upsertFriends(diary.getId());
        Share s2 = shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.PUBLIC, null);

        assertThat(s2.getId()).isEqualTo(s1.getId());
        assertThat(shareRepository.count()).isEqualTo(1);
        assertThat(shareRepository.findById(s1.getId()).orElseThrow().getVisibility())
                .isEqualTo(ShareVisibility.PUBLIC);
    }

    @Test
    void PRIVATE_전환은_행과_답글을_남기고_열람만_차단하고_재공개하면_복원된다() {
        Share share = upsertFriends(diary.getId());
        jdbc.update("INSERT INTO share_replies(share_id,author_id,content) VALUES (?,?, '답글')",
                share.getId(), f1Id);

        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.PRIVATE, null);

        assertThat(shareRepository.findById(share.getId())).isPresent();
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(f1Id, share.getId()));
        assertThat(shareService.feed(f1Id, null, 20)).isEmpty();
        // PRIVATE 행은 내 관리 목록에서 제외
        assertThat(shareService.mine(meId)).isEmpty();

        // 재공개 — 답글·열람이 그대로 복원된다
        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.FRIENDS, null);
        assertThat(shareService.detail(f1Id, share.getId()).replyCount()).isEqualTo(1);
        assertThat(shareService.feed(f1Id, null, 20)).hasSize(1);
    }

    @Test
    void 타인_항목과_미존재_항목은_404() {
        Diary others = diaryService.create(f1Id, LocalDate.of(2027, 5, 21), "남의 일기",
                "본문", null, null, null);

        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.upsert(meId, ShareItemType.DIARY, others.getId(),
                        ShareVisibility.FRIENDS, null));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.upsert(meId, ShareItemType.DIARY, 999999L,
                        ShareVisibility.FRIENDS, null));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.upsert(meId, ShareItemType.EVENT, 999999L,
                        ShareVisibility.FRIENDS, null));
    }

    @Test
    void 제목_없는_일기도_공개된다() {
        // Diary.title은 nullable — null이 "항목 부재"로 오인되면 안 된다
        Diary untitled = diaryService.create(meId, LocalDate.of(2027, 5, 22), null,
                "본문만", null, null, null);

        Share share = upsertFriends(untitled.getId());

        assertThat(shareRepository.findById(share.getId())).isPresent();
        assertThat(shareService.feed(f1Id, null, 20).get(0).itemTitle()).isNull();
    }

    @Test
    void GROUP_설정은_groupId가_필수이고_본인_그룹만_가능하다() {
        var f1Group = groupService.create(f1Id, "f1 그룹", List.of(meId));

        // groupId 누락
        assertError(ErrorCode.VISIBILITY_INVALID,
                () -> shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                        ShareVisibility.GROUP, null));
        // 타인 그룹
        assertError(ErrorCode.VISIBILITY_INVALID,
                () -> shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                        ShareVisibility.GROUP, f1Group.groupId()));
        // GROUP 아닌 범위에 groupId를 보내면 거절
        assertError(ErrorCode.VISIBILITY_INVALID,
                () -> shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                        ShareVisibility.FRIENDS, f1Group.groupId()));
        assertThat(shareRepository.count()).isZero();
    }

    @Test
    void 피드는_범위에_포함된_친구에게만_노출된다() {
        // FRIENDS — 친구 전원에게 보이고 비친구는 안 보인다
        Share friendsShare = upsertFriends(diary.getId());
        assertThat(shareService.feed(f1Id, null, 20)).hasSize(1);
        assertThat(shareService.feed(f2Id, null, 20)).hasSize(1);
        assertThat(shareService.feed(outsiderId, null, 20)).isEmpty();

        // GROUP — 그룹 멤버(f1)만 보이고 비멤버 친구(f2)는 안 보인다
        Diary second = diaryService.create(meId, LocalDate.of(2027, 5, 21), "둘째",
                "본문", null, null, null);
        var group = groupService.create(meId, "그룹", List.of(f1Id));
        Share groupShare = shareService.upsert(meId, ShareItemType.DIARY, second.getId(),
                ShareVisibility.GROUP, group.groupId());

        assertThat(shareService.feed(f1Id, null, 20)).extracting(FeedItem::shareId)
                .containsExactly(groupShare.getId(), friendsShare.getId());
        assertThat(shareService.feed(f2Id, null, 20)).extracting(FeedItem::shareId)
                .containsExactly(friendsShare.getId());

        // 커서 경계 — id < cursor
        assertThat(shareService.feed(f1Id, groupShare.getId(), 20))
                .extracting(FeedItem::shareId).containsExactly(friendsShare.getId());

        assertError(ErrorCode.COMMON_VALIDATION,
                () -> shareService.feed(f1Id, null, 0));
        assertError(ErrorCode.COMMON_VALIDATION,
                () -> shareService.feed(f1Id, null, 51));
    }

    @Test
    void 피드_항목은_미리보기를_포함한다() {
        // 일기 — 본문 발췌 (140자 초과 시 말줄임)
        Diary longDiary = diaryService.create(meId, LocalDate.of(2027, 5, 21), "긴 일기",
                "가".repeat(200), null, null, null);
        upsertFriends(longDiary.getId());
        FeedItem diaryItem = shareService.feed(f1Id, null, 20).get(0);
        assertThat(diaryItem.itemPreview()).hasSize(141).endsWith("…");

        // 일정 — "M월 d일 (요일) HH:mm–HH:mm · 장소 — 설명" 조합
        jdbc.update("INSERT INTO events(user_id,title,description,start_time,end_time,location) "
                + "VALUES (?,'회의','안건 정리','2027-06-01 10:00:00','2027-06-01 11:30:00','연남동')",
                meId);
        Long eventId = jdbc.queryForObject("SELECT id FROM events LIMIT 1", Long.class);
        shareService.upsert(meId, ShareItemType.EVENT, eventId,
                ShareVisibility.FRIENDS, null);
        FeedItem eventItem = shareService.feed(f1Id, null, 20).get(0);
        assertThat(eventItem.itemPreview())
                .isEqualTo("6월 1일 (화) 10:00–11:30 · 연남동 — 안건 정리");
    }

    @Test
    void PUBLIC도_V2에서는_친구에게만_노출된다() {
        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.PUBLIC, null);

        assertThat(shareService.feed(f1Id, null, 20)).hasSize(1);
        assertThat(shareService.feed(outsiderId, null, 20)).isEmpty();
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(outsiderId,
                        shareRepository.findAll().get(0).getId()));
    }

    @Test
    void detail은_소유자와_canView_통과자만_조회한다() {
        Share share = upsertFriends(diary.getId());

        var ownerView = shareService.detail(meId, share.getId());
        assertThat(ownerView.item()).isInstanceOf(app.weave.common.port.SharedDiary.class);
        assertThat(ownerView.visibility()).isEqualTo(ShareVisibility.FRIENDS);

        assertThat(shareService.detail(f1Id, share.getId()).item()).isNotNull();
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(outsiderId, share.getId()));
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(meId, 999999L));
    }

    @Test
    void 친구해제_즉시_비가시이고_재친구하면_복원된다() {
        Share share = upsertFriends(diary.getId());
        jdbc.update("DELETE FROM friendships WHERE user_id=? AND friend_id=?", meId, f1Id);

        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(f1Id, share.getId()));
        assertThat(shareService.feed(f1Id, null, 20)).isEmpty();
        assertThat(shareService.feed(outsiderId, null, 20)).isEmpty();

        // 재친구 — 열어둠 모델이라 공개 항목이 그대로 다시 보인다
        befriend(meId, f1Id);
        assertThat(shareService.detail(f1Id, share.getId()).item()).isNotNull();
        assertThat(shareService.feed(f1Id, null, 20)).hasSize(1);
    }

    @Test
    void GROUP_항목도_친구해제_즉시_비가시이고_재친구하면_복원된다() {
        // 그룹 멤버 행은 해제로 지워지지 않는다 — friendship 합성이 비가시를 보장한다
        var group = groupService.create(meId, "그룹", List.of(f1Id));
        Share share = shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.GROUP, group.groupId());
        assertThat(shareService.feed(f1Id, null, 20)).hasSize(1);

        jdbc.update("DELETE FROM friendships WHERE user_id=? AND friend_id=?", meId, f1Id);
        assertThat(shareService.feed(f1Id, null, 20)).isEmpty();
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(f1Id, share.getId()));

        befriend(meId, f1Id);
        assertThat(shareService.feed(f1Id, null, 20)).hasSize(1);
        assertThat(shareService.detail(f1Id, share.getId()).item()).isNotNull();
    }

    @Test
    void PRIVATE에서_FRIENDS로_상향하면_구독자에게_알림이_간다() {
        groupService.subscribe(f1Id, meId);

        // PRIVATE 신규 공개 — 아무도 못 보니 무알림
        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.PRIVATE, null);
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(0);

        // PRIVATE→FRIENDS 상향 — f1이 새로 보이게 되므로 SHARED_ITEM 발행
        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.FRIENDS, null);
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);
    }

    @Test
    void 그룹삭제는_참조_공개항목을_비공개취급한다() {
        var group = groupService.create(meId, "그룹", List.of(f1Id));
        Share share = shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.GROUP, group.groupId());

        groupService.delete(meId, group.groupId());

        // group_id SET NULL → 소유자 외 비가시 = 비공개 취급
        Share reloaded = shareRepository.findById(share.getId()).orElseThrow();
        assertThat(reloaded.getGroupId()).isNull();
        assertThat(reloaded.getVisibility()).isEqualTo(ShareVisibility.GROUP);
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.detail(f1Id, share.getId()));
        assertThat(shareService.feed(f1Id, null, 20)).isEmpty();
        assertThat(shareService.detail(meId, share.getId()).item()).isNotNull();
    }

    @Test
    void 원본삭제후_피드와_목록과_상세에서_비노출이다() {
        Share share = upsertFriends(diary.getId());
        jdbc.update("DELETE FROM diaries WHERE id=?", diary.getId());

        assertThat(shareService.feed(f1Id, null, 20)).isEmpty();
        assertThat(shareService.mine(meId)).isEmpty();
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.detail(f1Id, share.getId()));
        // share 행 자체는 남는다
        assertThat(shareRepository.findById(share.getId())).isPresent();
    }

    @Test
    void by_owner는_친구에게만_보이는_항목을_반환한다() {
        Share friendsShare = upsertFriends(diary.getId());
        Diary second = diaryService.create(meId, LocalDate.of(2027, 5, 21), "둘째",
                "본문", null, null, null);
        var group = groupService.create(meId, "그룹", List.of(f1Id));
        Share groupShare = shareService.upsert(meId, ShareItemType.DIARY, second.getId(),
                ShareVisibility.GROUP, group.groupId());

        // 멤버 f1은 둘 다, 비멤버 f2는 FRIENDS 항목만
        assertThat(shareService.byOwner(f1Id, meId)).extracting(FeedItem::shareId)
                .containsExactlyInAnyOrder(friendsShare.getId(), groupShare.getId());
        assertThat(shareService.byOwner(f2Id, meId)).extracting(FeedItem::shareId)
                .containsExactly(friendsShare.getId());

        // 비친구는 존재 비노출 404
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.byOwner(outsiderId, meId));
    }

    @Test
    void 사진은_열람자에게만_서빙되고_EVENT공유는_404다() {
        DiaryPhoto photo = photoService.upload(meId, diary, jpeg());
        Share share = upsertFriends(diary.getId());

        SharedPhotoBytes bytes = shareService.photo(f1Id, share.getId(), photo.getId());
        assertThat(bytes.contentType()).isEqualTo("image/jpeg");
        assertThat(bytes.bytes()).isEqualTo(jpeg());
        assertThat(shareService.photo(meId, share.getId(), photo.getId()).bytes())
                .isEqualTo(jpeg());

        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> shareService.photo(outsiderId, share.getId(), photo.getId()));
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.photo(f1Id, share.getId(), 999999L));

        jdbc.update("INSERT INTO events(user_id,title,start_time,end_time) "
                + "VALUES (?,'회의','2027-06-01 10:00:00','2027-06-01 11:00:00')", meId);
        Long eventId = jdbc.queryForObject("SELECT id FROM events LIMIT 1", Long.class);
        Share eventShare = shareService.upsert(meId, ShareItemType.EVENT, eventId,
                ShareVisibility.FRIENDS, null);
        assertError(ErrorCode.SHARE_ITEM_NOT_FOUND,
                () -> shareService.photo(f1Id, eventShare.getId(), photo.getId()));
    }

    @Test
    void 구독한_친구의_신규공개는_알림이_가고_비구독과_비공개는_안_간다() {
        groupService.subscribe(f1Id, meId);

        // FRIENDS 신규 공개 — f1이 볼 수 있으므로 알림
        upsertFriends(diary.getId());
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);
        // 비구독 친구 f2는 무알림
        assertThat(notificationCount(f2Id, "SHARED_ITEM")).isEqualTo(0);
        assertThat(notificationCount(meId, "SHARED_ITEM")).isEqualTo(0);

        // PRIVATE 전환은 새로 보이는 대상이 없어 무알림, 비공개 신규 항목도 무알림
        Diary second = diaryService.create(meId, LocalDate.of(2027, 5, 21), "둘째",
                "본문", null, null, null);
        shareService.upsert(meId, ShareItemType.DIARY, second.getId(),
                ShareVisibility.PRIVATE, null);
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);
    }

    @Test
    void 범위를_상향해_새로_보이게된_구독자에게만_알림이_간다() {
        groupService.subscribe(f1Id, meId);
        groupService.subscribe(f2Id, meId);
        var group = groupService.create(meId, "그룹", List.of(f1Id));

        // GROUP 공개 — 멤버 f1만 알림, 비멤버 f2는 못 보니 무알림
        Share share = shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.GROUP, group.groupId());
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);
        assertThat(notificationCount(f2Id, "SHARED_ITEM")).isEqualTo(0);

        // 같은 설정 재저장 — 새로 보이는 대상이 없어 중복 알림 없음
        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.GROUP, group.groupId());
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);

        // FRIENDS로 상향 — f2가 새로 보이게 되므로 알림, f1은 이미 보던 중이라 무알림
        shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.FRIENDS, null);
        assertThat(notificationCount(f2Id, "SHARED_ITEM")).isEqualTo(1);
        assertThat(notificationCount(f1Id, "SHARED_ITEM")).isEqualTo(1);
    }
}
