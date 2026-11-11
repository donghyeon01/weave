package app.weave.share.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.diary.domain.Diary;
import app.weave.diary.domain.Diary.Mood;
import app.weave.diary.service.DiaryService;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareItemType;
import app.weave.share.domain.ShareReply;
import app.weave.share.domain.ShareReplyRepository;
import app.weave.share.domain.ShareVisibility;
import app.weave.share.service.ReplyService.ReplyNode;
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
class ReplyServiceTest {

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
    ReplyService replyService;
    @Autowired
    ShareService shareService;
    @Autowired
    ShareReplyRepository replyRepository;
    @Autowired
    DiaryService diaryService;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long f1Id;
    private Long f2Id;
    private Long outsiderId;
    private Share share;

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
        Diary diary = diaryService.create(meId, LocalDate.of(2027, 5, 20), "제주 여행",
                "본문", Mood.HAPPY, null, List.of("제주"));
        share = shareService.upsert(meId, ShareItemType.DIARY, diary.getId(),
                ShareVisibility.FRIENDS, null);
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
    void 루트와_대댓글_작성은_소유자에게_SHARE_REPLY_알림을_보낸다() {
        ShareReply root = replyService.create(f1Id, share.getId(), "잘 봤어", null);
        ShareReply child = replyService.create(f2Id, share.getId(), "나도", root.getId());

        assertThat(root.getParentId()).isNull();
        assertThat(child.getParentId()).isEqualTo(root.getId());
        assertThat(notificationCount(meId, "SHARE_REPLY")).isEqualTo(2);
        assertThat(notificationCount(f1Id, "SHARE_REPLY")).isEqualTo(0);
    }

    @Test
    void 소유자_자신의_답글은_알림이_없다() {
        replyService.create(meId, share.getId(), "내 글에 메모", null);

        assertThat(notificationCount(meId, "SHARE_REPLY")).isEqualTo(0);
    }

    @Test
    void 대댓글의_대댓글은_3단이라_400이다() {
        ShareReply root = replyService.create(f1Id, share.getId(), "루트", null);
        ShareReply child = replyService.create(meId, share.getId(), "대댓", root.getId());

        assertError(ErrorCode.REPLY_PARENT_INVALID,
                () -> replyService.create(f1Id, share.getId(), "3단", child.getId()));
    }

    @Test
    void 다른_share의_답글을_부모로_지정하면_400이다() {
        Diary other = diaryService.create(meId, LocalDate.of(2027, 5, 21), "둘째",
                "본문", null, null, null);
        Share share2 = shareService.upsert(meId, ShareItemType.DIARY, other.getId(),
                ShareVisibility.FRIENDS, null);
        ShareReply foreignRoot = replyService.create(f1Id, share2.getId(), "타 공유 루트", null);

        assertError(ErrorCode.REPLY_PARENT_INVALID,
                () -> replyService.create(f1Id, share.getId(), "교차", foreignRoot.getId()));
    }

    @Test
    void tombstone_답글은_부모가_될_수_없다() {
        ShareReply root = replyService.create(f1Id, share.getId(), "루트", null);
        replyService.delete(f1Id, share.getId(), root.getId());

        assertError(ErrorCode.REPLY_PARENT_INVALID,
                () -> replyService.create(f2Id, share.getId(), "대댓", root.getId()));
    }

    @Test
    void 공백이나_501자_답글은_400이다() {
        assertError(ErrorCode.REPLY_INVALID,
                () -> replyService.create(f1Id, share.getId(), "   ", null));
        assertError(ErrorCode.REPLY_INVALID,
                () -> replyService.create(f1Id, share.getId(), null, null));
        assertError(ErrorCode.REPLY_INVALID,
                () -> replyService.create(f1Id, share.getId(), "가".repeat(501), null));
        assertThat(replyRepository.count()).isZero();
    }

    @Test
    void 비열람자는_작성과_조회가_404다() {
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> replyService.create(outsiderId, share.getId(), "답글", null));
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> replyService.list(outsiderId, share.getId(), null, 20));
        assertError(ErrorCode.SHARE_NOT_FOUND,
                () -> replyService.list(f1Id, 999999L, null, 20));
    }

    @Test
    void 목록은_스레드_구조와_닉네임을_반환한다() {
        ShareReply root = replyService.create(f1Id, share.getId(), "루트", null);
        replyService.create(meId, share.getId(), "소유자 대댓", root.getId());
        replyService.create(f2Id, share.getId(), "두번째 루트", null);

        List<ReplyNode> nodes = replyService.list(f1Id, share.getId(), null, 20);

        assertThat(nodes).hasSize(2);
        ReplyNode first = nodes.get(0);
        assertThat(first.id()).isEqualTo(root.getId());
        assertThat(first.nickname()).isEqualTo("일");
        assertThat(first.deleted()).isFalse();
        assertThat(first.children()).hasSize(1);
        assertThat(first.children().get(0).nickname()).isEqualTo("나");
        assertThat(first.children().get(0).children()).isEmpty();
    }

    @Test
    void 수정은_작성자만_가능하고_타인과_tombstone은_404다() {
        ShareReply reply = replyService.create(f1Id, share.getId(), "원문", null);

        // 다른 열람자·소유자도 작성자가 아니면 REPLY_NOT_AUTHOR
        assertError(ErrorCode.REPLY_NOT_AUTHOR,
                () -> replyService.update(f2Id, share.getId(), reply.getId(), "수정"));
        assertError(ErrorCode.REPLY_NOT_AUTHOR,
                () -> replyService.update(meId, share.getId(), reply.getId(), "수정"));

        replyService.update(f1Id, share.getId(), reply.getId(), "고침");
        assertThat(replyRepository.findById(reply.getId()).orElseThrow().getContent())
                .isEqualTo("고침");

        assertError(ErrorCode.REPLY_INVALID,
                () -> replyService.update(f1Id, share.getId(), reply.getId(), " "));

        // tombstone은 REPLY_NOT_FOUND
        replyService.delete(f1Id, share.getId(), reply.getId());
        assertError(ErrorCode.REPLY_NOT_FOUND,
                () -> replyService.update(f1Id, share.getId(), reply.getId(), "다시"));
    }

    @Test
    void 타_share_답글이나_없는_답글은_404다() {
        assertError(ErrorCode.REPLY_NOT_FOUND,
                () -> replyService.update(f1Id, share.getId(), 999999L, "수정"));
        assertError(ErrorCode.REPLY_NOT_FOUND,
                () -> replyService.delete(f1Id, share.getId(), 999999L));
    }

    @Test
    void 삭제는_tombstone이고_자식은_보존된다() {
        ShareReply root = replyService.create(f1Id, share.getId(), "루트", null);
        ShareReply child = replyService.create(meId, share.getId(), "대댓", root.getId());

        replyService.delete(meId, share.getId(), root.getId()); // 소유자가 삭제

        List<ReplyNode> nodes = replyService.list(f1Id, share.getId(), null, 20);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).deleted()).isTrue();
        assertThat(nodes.get(0).content()).isEmpty(); // tombstone 마스킹
        assertThat(nodes.get(0).children()).hasSize(1);
        assertThat(nodes.get(0).children().get(0).id()).isEqualTo(child.getId());
        assertThat(nodes.get(0).children().get(0).content()).isEqualTo("대댓");

        // 이미 tombstone인 답글의 재삭제는 404
        assertError(ErrorCode.REPLY_NOT_FOUND,
                () -> replyService.delete(meId, share.getId(), root.getId()));
    }

    @Test
    void 삭제권한은_작성자와_공유소유자뿐이다() {
        ShareReply f1Reply = replyService.create(f1Id, share.getId(), "f1 답글", null);

        // 다른 대상(f2)은 작성자도 소유자도 아니다
        assertError(ErrorCode.REPLY_NOT_AUTHOR,
                () -> replyService.delete(f2Id, share.getId(), f1Reply.getId()));

        replyService.delete(f1Id, share.getId(), f1Reply.getId()); // 작성자 삭제
        assertThat(replyRepository.findById(f1Reply.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    @Test
    void 루트_페이지네이션은_id_cursor_오름차순이고_대댓글은_커서대상이_아니다() {
        for (int i = 0; i < 3; i++) {
            ShareReply r = replyService.create(f1Id, share.getId(), "루트" + i, null);
            replyService.create(meId, share.getId(), "대댓" + i, r.getId());
        }

        List<ReplyNode> page1 = replyService.list(f1Id, share.getId(), null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).content()).isEqualTo("루트0");
        assertThat(page1.get(1).content()).isEqualTo("루트1");

        List<ReplyNode> page2 = replyService.list(f1Id, share.getId(),
                page1.get(1).id(), 2);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).content()).isEqualTo("루트2");

        assertError(ErrorCode.COMMON_VALIDATION,
                () -> replyService.list(f1Id, share.getId(), null, 0));
        assertError(ErrorCode.COMMON_VALIDATION,
                () -> replyService.list(f1Id, share.getId(), null, 51));
    }

    @Test
    void 목록과_상세의_replyCount는_살아있는_답글만_센다() {
        ShareReply root = replyService.create(f1Id, share.getId(), "루트", null);
        replyService.create(meId, share.getId(), "대댓", root.getId());
        replyService.delete(f1Id, share.getId(), root.getId());

        assertThat(shareService.detail(meId, share.getId()).replyCount()).isEqualTo(1);
        assertThat(shareService.mine(meId).get(0).replyCount()).isEqualTo(1);
    }
}
