package app.weave.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.friend.domain.FeedSubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class FriendGroupServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    FriendGroupService groupService;
    @Autowired
    FeedSubscriptionRepository subscriptionRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long f1Id;
    private Long f2Id;
    private Long outsiderId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM share_replies");
        jdbc.update("DELETE FROM shares");
        jdbc.update("DELETE FROM feed_subscriptions");
        jdbc.update("DELETE FROM friend_group_members");
        jdbc.update("DELETE FROM friend_groups");
        jdbc.update("DELETE FROM notifications");
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

    @Test
    void 그룹은_최대_3개까지고_4개째는_409다() {
        groupService.create(meId, "그룹1", List.of(f1Id));
        groupService.create(meId, "그룹2", List.of(f1Id));
        groupService.create(meId, "그룹3", List.of(f2Id));

        assertError(ErrorCode.GROUP_LIMIT_EXCEEDED,
                () -> groupService.create(meId, "그룹4", List.of(f1Id)));
        assertThat(groupService.myGroups(meId)).hasSize(3);
    }

    @Test
    void 멤버는_친구만_가능하고_본인이나_비친구는_400이다() {
        assertError(ErrorCode.GROUP_MEMBER_INVALID,
                () -> groupService.create(meId, "그룹", List.of(outsiderId)));
        assertError(ErrorCode.GROUP_MEMBER_INVALID,
                () -> groupService.create(meId, "그룹", List.of(meId)));
        // 친구 한 명이라도 비친구가 섞이면 전체 거절
        assertError(ErrorCode.GROUP_MEMBER_INVALID,
                () -> groupService.create(meId, "그룹", List.of(f1Id, outsiderId)));

        var group = groupService.create(meId, "그룹", List.of(f1Id));
        assertError(ErrorCode.GROUP_MEMBER_INVALID,
                () -> groupService.replaceMembers(meId, group.groupId(),
                        List.of(outsiderId)));
        // 거절 시 기존 멤버십은 보존
        assertThat(groupService.myGroups(meId).get(0).memberIds()).containsExactly(f1Id);
    }

    @Test
    void 멤버_전량교체와_이름변경과_삭제가_동작한다() {
        var group = groupService.create(meId, "원래", List.of(f1Id));

        var renamed = groupService.rename(meId, group.groupId(), "바뀜");
        assertThat(renamed.name()).isEqualTo("바뀜");

        var replaced = groupService.replaceMembers(meId, group.groupId(),
                List.of(f1Id, f2Id));
        assertThat(replaced.memberIds()).containsExactlyInAnyOrder(f1Id, f2Id);
        assertThat(groupService.isGroupMember(group.groupId(), f2Id)).isTrue();

        // 빈 배열 교체로 그룹을 비운다
        var emptied = groupService.replaceMembers(meId, group.groupId(), List.of());
        assertThat(emptied.memberIds()).isEmpty();

        groupService.delete(meId, group.groupId());
        assertThat(groupService.myGroups(meId)).isEmpty();
        assertError(ErrorCode.GROUP_NOT_FOUND,
                () -> groupService.delete(meId, group.groupId()));
    }

    @Test
    void 타인_그룹은_조회도_편집도_404다() {
        var group = groupService.create(f1Id, "f1 그룹", List.of(meId));

        assertThat(groupService.ownsGroup(meId, group.groupId())).isFalse();
        assertThat(groupService.ownsGroup(f1Id, group.groupId())).isTrue();
        assertError(ErrorCode.GROUP_NOT_FOUND,
                () -> groupService.rename(meId, group.groupId(), "탈취"));
        assertError(ErrorCode.GROUP_NOT_FOUND,
                () -> groupService.replaceMembers(meId, group.groupId(), List.of(f2Id)));
        assertError(ErrorCode.GROUP_NOT_FOUND,
                () -> groupService.delete(meId, group.groupId()));
    }

    @Test
    void 구독은_친구만_가능하고_비친구와_본인은_400이다() {
        assertError(ErrorCode.SUBSCRIPTION_INVALID,
                () -> groupService.subscribe(meId, outsiderId));
        assertError(ErrorCode.SUBSCRIPTION_INVALID,
                () -> groupService.subscribe(meId, meId));

        groupService.subscribe(meId, f1Id);
        assertThat(subscriptionRepository
                .existsBySubscriberIdAndFriendUserId(meId, f1Id)).isTrue();

        // 중복 구독은 멱등
        groupService.subscribe(meId, f1Id);
        assertThat(subscriptionRepository.count()).isEqualTo(1);

        groupService.unsubscribe(meId, f1Id);
        assertThat(subscriptionRepository
                .existsBySubscriberIdAndFriendUserId(meId, f1Id)).isFalse();
        // 미구독 해제도 에러 없이 멱등
        groupService.unsubscribe(meId, f1Id);
    }

    @Test
    void 친구해제로_멤버행과_구독행은_지우지_않는다() {
        var group = groupService.create(meId, "그룹", List.of(f1Id));
        groupService.subscribe(f1Id, meId);
        jdbc.update("DELETE FROM friendships WHERE user_id=? AND friend_id=?", meId, f1Id);

        // 행은 남는다 — 비가시는 friendship 실시간 검증이 보장하고 재친구 시 복원된다
        assertThat(groupService.isGroupMember(group.groupId(), f1Id)).isTrue();
        assertThat(subscriptionRepository
                .existsBySubscriberIdAndFriendUserId(f1Id, meId)).isTrue();
    }
}
