package app.weave.availability.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.weave.availability.domain.AvailabilityBlock;
import app.weave.availability.domain.AvailabilityBlockRepository;
import app.weave.availability.domain.AvailabilityPreference;
import app.weave.availability.domain.AvailabilityPreferenceRepository;
import app.weave.availability.domain.AvailabilityScope;
import app.weave.availability.domain.AvailabilityScopeRepository;
import app.weave.availability.domain.ScopeMode;
import app.weave.common.port.AvailabilityBlockInfo;
import app.weave.common.port.AvailabilityMode;
import app.weave.common.port.AvailabilityScopeInfo;
import app.weave.common.port.UserAvailability;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
class AvailabilityAccessAdapterTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    AvailabilityAccessAdapter adapter;
    @Autowired
    AvailabilityScopeRepository scopeRepository;
    @Autowired
    AvailabilityBlockRepository blockRepository;
    @Autowired
    AvailabilityPreferenceRepository preferenceRepository;
    @Autowired
    JdbcTemplate jdbc;

    private Long meId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM availability_blocks");
        jdbc.update("DELETE FROM availability_scopes");
        jdbc.update("DELETE FROM availability_preferences");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@weave.local'");
        jdbc.update("INSERT INTO users(email,name,nickname,provider) VALUES "
                + "('me@weave.local','나','나','demo'),('other@weave.local','타인','타인','demo')");
        meId = jdbc.queryForObject("SELECT id FROM users WHERE email='me@weave.local'",
                Long.class);
        otherId = jdbc.queryForObject("SELECT id FROM users WHERE email='other@weave.local'",
                Long.class);
    }

    private AvailabilityScope scope(Long userId, ScopeMode mode, LocalDate start,
            LocalDate end, boolean complete) {
        AvailabilityScope s = scopeRepository.save(
                new AvailabilityScope(userId, mode, start, end));
        if (complete) {
            s.complete();
            s = scopeRepository.save(s);
        }
        return s;
    }

    @Test
    void COMPLETED_scope만_반환되고_INPUT은_제외된다() {
        scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 7),
                false);
        scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 8), LocalDate.of(2027, 6, 14),
                true);

        Map<Long, UserAvailability> result = adapter.availabilityOf(List.of(meId),
                AvailabilityMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30));

        assertThat(result).containsKey(meId);
        assertThat(result.get(meId).scopes()).hasSize(1);
        assertThat(result.get(meId).scopes().get(0).rangeStart())
                .isEqualTo(LocalDate.of(2027, 6, 8));
    }

    @Test
    void mode_불일치_scope는_제외된다() {
        scope(meId, ScopeMode.DATE, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 7), true);
        scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 7), true);

        Map<Long, UserAvailability> result = adapter.availabilityOf(List.of(meId),
                AvailabilityMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30));

        assertThat(result.get(meId).scopes()).hasSize(1);
    }

    @Test
    void 조회_범위_밖_scope는_제외된다() {
        scope(meId, ScopeMode.TIME, LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 7), true);
        scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 7), true);

        Map<Long, UserAvailability> result = adapter.availabilityOf(List.of(meId),
                AvailabilityMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30));

        assertThat(result.get(meId).scopes()).hasSize(1);
        assertThat(result.get(meId).scopes().get(0).rangeStart())
                .isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void 블록은_자기_scope에_귀속된다() {
        AvailabilityScope a = scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 1),
                LocalDate.of(2027, 6, 7), true);
        AvailabilityScope b = scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 8),
                LocalDate.of(2027, 6, 14), true);
        blockRepository.save(AvailabilityBlock.time(a.getId(),
                LocalDateTime.of(2027, 6, 2, 10, 0), LocalDateTime.of(2027, 6, 2, 12, 0)));
        blockRepository.save(AvailabilityBlock.date(b.getId(), LocalDate.of(2027, 6, 9)));

        Map<Long, UserAvailability> result = adapter.availabilityOf(List.of(meId),
                AvailabilityMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30));

        List<AvailabilityScopeInfo> scopes = result.get(meId).scopes();
        assertThat(scopes).hasSize(2);
        AvailabilityScopeInfo first = scopes.get(0);
        AvailabilityScopeInfo second = scopes.get(1);
        assertThat(first.rangeStart()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(first.blocks()).containsExactly(
                new AvailabilityBlockInfo(null, LocalDateTime.of(2027, 6, 2, 10, 0),
                        LocalDateTime.of(2027, 6, 2, 12, 0)));
        assertThat(second.rangeStart()).isEqualTo(LocalDate.of(2027, 6, 8));
        assertThat(second.blocks()).containsExactly(
                new AvailabilityBlockInfo(LocalDate.of(2027, 6, 9), null, null));
    }

    @Test
    void preference_행이_없으면_useCalendarInference는_false다() {
        scope(meId, ScopeMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 7), true);
        preferenceRepository.save(new AvailabilityPreference(otherId, true));

        Map<Long, UserAvailability> result = adapter.availabilityOf(List.of(meId, otherId),
                AvailabilityMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30));

        assertThat(result.get(meId).useCalendarInference()).isFalse();
        assertThat(result.get(otherId).useCalendarInference()).isTrue();
        assertThat(result.get(otherId).scopes()).isEmpty();
    }

    @Test
    void 빈_userIds는_빈_맵을_반환한다() {
        Map<Long, UserAvailability> result = adapter.availabilityOf(List.of(),
                AvailabilityMode.TIME, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30));

        assertThat(result).isEmpty();
    }
}
