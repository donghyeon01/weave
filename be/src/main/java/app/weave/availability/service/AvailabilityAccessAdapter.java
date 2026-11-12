package app.weave.availability.service;

import app.weave.availability.domain.AvailabilityBlock;
import app.weave.availability.domain.AvailabilityBlockRepository;
import app.weave.availability.domain.AvailabilityPreference;
import app.weave.availability.domain.AvailabilityPreferenceRepository;
import app.weave.availability.domain.AvailabilityScope;
import app.weave.availability.domain.AvailabilityScopeRepository;
import app.weave.availability.domain.ScopeMode;
import app.weave.availability.domain.ScopeStatus;
import app.weave.common.port.AvailabilityAccess;
import app.weave.common.port.AvailabilityBlockInfo;
import app.weave.common.port.AvailabilityMode;
import app.weave.common.port.AvailabilityScopeInfo;
import app.weave.common.port.UserAvailability;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** AvailabilityAccess 구현 — availability 데이터의 읽기 전용 출구 (capability-map 조회 포트 규약). */
@Component
public class AvailabilityAccessAdapter implements AvailabilityAccess {

    private final AvailabilityScopeRepository scopeRepository;
    private final AvailabilityBlockRepository blockRepository;
    private final AvailabilityPreferenceRepository preferenceRepository;

    public AvailabilityAccessAdapter(AvailabilityScopeRepository scopeRepository,
            AvailabilityBlockRepository blockRepository,
            AvailabilityPreferenceRepository preferenceRepository) {
        this.scopeRepository = scopeRepository;
        this.blockRepository = blockRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /** N+1 없이 3쿼리 — scopes(모드·COMPLETED·범위 겹침) → blocks(scopeId 일괄) → preferences(userIds 일괄). */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, UserAvailability> availabilityOf(Collection<Long> userIds,
            AvailabilityMode mode, LocalDate from, LocalDate to) {
        Map<Long, UserAvailability> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        List<AvailabilityScope> scopes = scopeRepository
                .findByUserIdInAndModeAndStatusAndRangeStartLessThanEqualAndRangeEndGreaterThanEqualOrderByRangeStartAscIdAsc(
                        userIds, ScopeMode.valueOf(mode.name()), ScopeStatus.COMPLETED,
                        to, from);

        Set<Long> scopeIds = scopes.stream().map(AvailabilityScope::getId)
                .collect(Collectors.toSet());
        Map<Long, List<AvailabilityBlockInfo>> blocksByScope = new HashMap<>();
        if (!scopeIds.isEmpty()) {
            for (AvailabilityBlock b : blockRepository.findByScopeIdIn(scopeIds)) {
                blocksByScope.computeIfAbsent(b.getScopeId(), k -> new ArrayList<>())
                        .add(new AvailabilityBlockInfo(b.getBlockDate(), b.getStartAt(),
                                b.getEndAt()));
            }
        }

        Set<Long> inferringUserIds = preferenceRepository.findAllById(userIds).stream()
                .filter(AvailabilityPreference::isUseCalendarInference)
                .map(AvailabilityPreference::getUserId)
                .collect(Collectors.toSet());

        Map<Long, List<AvailabilityScopeInfo>> scopesByUser = new HashMap<>();
        for (AvailabilityScope s : scopes) {
            scopesByUser.computeIfAbsent(s.getUserId(), k -> new ArrayList<>())
                    .add(new AvailabilityScopeInfo(s.getRangeStart(), s.getRangeEnd(),
                            blocksByScope.getOrDefault(s.getId(), List.of())));
        }

        for (Long userId : userIds) {
            result.put(userId, new UserAvailability(
                    scopesByUser.getOrDefault(userId, List.of()),
                    inferringUserIds.contains(userId)));
        }
        return result;
    }
}
