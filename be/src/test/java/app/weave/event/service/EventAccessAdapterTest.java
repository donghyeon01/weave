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
    app.weave.event.domain.EventRepository eventRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long otherId;
    private Event event;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM schedule_proposal_participants");
        jdbc.update("DELETE FROM schedule_proposals");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'",
                Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
        event = eventService.create(meId, "점심 약속", "설명",
                LocalDateTime.of(2027, 6, 1, 12, 0), LocalDateTime.of(2027, 6, 1, 13, 0),
                "강남", false, null, null);
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

    @Test
    void proposal_출처_일정도_본인_소유면_SharedEvent다() {
        // 공유 허용 범위 — spec: proposal-sourced 이벤트도 본인 소유 행이면 공유 가능
        // fk_events_proposal이 실제 제안 행을 요구하므로 제안을 먼저 심는다
        jdbc.update("INSERT INTO schedule_proposals(organizer_id,title,mode,start_time,end_time,status) "
                + "VALUES (?,'조율 제안','TIME','2027-06-05 19:00:00','2027-06-05 21:00:00','CONFIRMED')",
                meId);
        Long proposalId = jdbc.queryForObject("SELECT id FROM schedule_proposals WHERE organizer_id=?",
                Long.class, meId);
        Event proposalEvent = eventRepository.save(new Event(meId, "조율 약속", null,
                LocalDateTime.of(2027, 6, 5, 19, 0), LocalDateTime.of(2027, 6, 5, 21, 0),
                null, false, Event.Source.PROPOSAL, proposalId, Event.BusyPolicy.HARD, null));

        Optional<SharedEvent> result = adapter.eventDetailOf(meId, proposalEvent.getId());

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("조율 약속");
    }
}
