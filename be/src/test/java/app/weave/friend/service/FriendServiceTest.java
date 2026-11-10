package app.weave.friend.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.weave.friend.domain.Friendship;
import app.weave.friend.domain.FriendshipRepository;
import java.util.Set;
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
class FriendServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    FriendService friendService;
    @Autowired
    FriendshipRepository friendshipRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long otherId;
    private Long thirdId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM friendships");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo'),"
                + "('third@weave.local','셋','셋','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'",
                Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
        thirdId = jdbc.queryForObject("SELECT id FROM users WHERE email='third@weave.local'",
                Long.class);
    }

    private Friendship accepted(Long userId, Long friendId) {
        Friendship f = friendshipRepository.save(new Friendship(userId, friendId, null));
        f.accept();
        return friendshipRepository.save(f);
    }

    @Test
    void ACCEPTED_관계는_양방향으로_friend다() {
        accepted(meId, otherId);

        assertThat(friendService.isFriend(meId, otherId)).isTrue();
        assertThat(friendService.isFriend(otherId, meId)).isTrue();
    }

    @Test
    void PENDING_관계는_friend가_아니다() {
        friendshipRepository.save(new Friendship(meId, otherId, null));

        assertThat(friendService.isFriend(meId, otherId)).isFalse();
        assertThat(friendService.isFriend(otherId, meId)).isFalse();
    }

    @Test
    void 무관계는_friend가_아니다() {
        assertThat(friendService.isFriend(meId, otherId)).isFalse();
    }

    @Test
    void acceptedFriendIds는_양방향_상대_id_집합이다() {
        accepted(meId, otherId);
        // 역방향(내가 friend_id 쪽) 행도 상대 id로 추출된다
        accepted(thirdId, meId);

        assertThat(friendService.acceptedFriendIds(meId))
                .containsExactlyInAnyOrder(otherId, thirdId);
    }
}
