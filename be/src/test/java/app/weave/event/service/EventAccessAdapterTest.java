package app.weave.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.weave.common.port.SharedEvent;
import app.weave.event.domain.Event;
import java.time.LocalDateTime;
import java.util.Optional;
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
class EventAccessAdapterTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    EventAccessAdapter adapter;
    @Autowired
    EventService eventService;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long otherId;
    private Event event;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'",
                Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
        event = eventService.create(meId, "점심 약속", "설명",
                LocalDateTime.of(2027, 6, 1, 12, 0), LocalDateTime.of(2027, 6, 1, 13, 0),
                "강남", false, null);
    }

    @Test
    void 소유_일정은_SharedEvent로_변환된다() {
        Optional<SharedEvent> result = adapter.eventDetailOf(meId, event.getId());

        assertThat(result).isPresent();
        SharedEvent shared = result.get();
        assertThat(shared.id()).isEqualTo(event.getId());
        assertThat(shared.userId()).isEqualTo(meId);
        assertThat(shared.title()).isEqualTo("점심 약속");
        assertThat(shared.description()).isEqualTo("설명");
        assertThat(shared.startTime()).isEqualTo(LocalDateTime.of(2027, 6, 1, 12, 0));
        assertThat(shared.endTime()).isEqualTo(LocalDateTime.of(2027, 6, 1, 13, 0));
        assertThat(shared.location()).isEqualTo("강남");
        assertThat(shared.allDay()).isFalse();
    }

    @Test
    void 타인_소유_일정은_empty다() {
        assertThat(adapter.eventDetailOf(otherId, event.getId())).isEmpty();
    }

    @Test
    void 미존재_일정은_empty다() {
        assertThat(adapter.eventDetailOf(meId, 999999L)).isEmpty();
    }
}
