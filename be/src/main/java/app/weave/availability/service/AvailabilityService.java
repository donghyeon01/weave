package app.weave.availability.service;

import app.weave.availability.domain.AvailabilityBlock;
import app.weave.availability.domain.AvailabilityBlockRepository;
import app.weave.availability.domain.AvailabilityPreference;
import app.weave.availability.domain.AvailabilityPreferenceRepository;
import app.weave.availability.domain.AvailabilityScope;
import app.weave.availability.domain.AvailabilityScopeRepository;
import app.weave.availability.domain.BlockKind;
import app.weave.availability.domain.PreferredWindow;
import app.weave.availability.domain.PreferredWindowRepository;
import app.weave.availability.domain.ScopeMode;
import app.weave.availability.domain.ScopeStatus;
import app.weave.auth.domain.UserRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가용성 범위·블록·선호의 본인 전용 CRUD — api-contract §3.12.
 * 타인 소유 행은 전부 404로 처리한다 (소유권 규약 — 존재 자체를 숨긴다).
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    /** 활성 범위 중첩 검사에 쓰는 상태 — CANCELLED는 생성 경로가 없지만 방어적으로 제외 목록에 둔다. */
    private static final List<ScopeStatus> ACTIVE_STATUSES =
            List.of(ScopeStatus.INPUT, ScopeStatus.COMPLETED);

    /** 선호 윈도우 사용자당 행 상한 — 계약 §3.12. */
    private static final int MAX_PREFERRED_WINDOWS = 30;

    private final AvailabilityScopeRepository scopeRepository;
    private final AvailabilityBlockRepository blockRepository;
    private final AvailabilityPreferenceRepository preferenceRepository;
    private final PreferredWindowRepository preferredWindowRepository;
    private final UserRepository userRepository;

    public AvailabilityService(AvailabilityScopeRepository scopeRepository,
            AvailabilityBlockRepository blockRepository,
            AvailabilityPreferenceRepository preferenceRepository,
            PreferredWindowRepository preferredWindowRepository,
            UserRepository userRepository) {
        this.scopeRepository = scopeRepository;
        this.blockRepository = blockRepository;
        this.preferenceRepository = preferenceRepository;
        this.preferredWindowRepository = preferredWindowRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ScopeWithBlocks createScope(Long userId, ScopeMode mode, LocalDate rangeStart,
            LocalDate rangeEnd) {
        if (rangeStart.isAfter(rangeEnd)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
        }
        // check-then-insert의 동시성 — user 행을 잠가 같은 사용자의 범위 생성을 직렬화한다.
        // 인증된 사용자는 반드시 행이 있으므로 orElseThrow 없이 잠금만 취한다.
        userRepository.findByIdForUpdate(userId);
        if (scopeRepository.existsOverlappingActive(userId, mode, ACTIVE_STATUSES,
                rangeStart, rangeEnd)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_SCOPE_CONFLICT);
        }
        AvailabilityScope scope = scopeRepository.save(
                new AvailabilityScope(userId, mode, rangeStart, rangeEnd));
        return new ScopeWithBlocks(scope, List.of());
    }

    public List<ScopeSummary> listScopes(Long userId, LocalDate from, LocalDate to) {
        List<AvailabilityScope> scopes = scopeRepository.findByUserIdOverlapping(
                userId, from, to);
        List<Long> ids = scopes.stream().map(AvailabilityScope::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : blockRepository.countByScopeIdIn(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return scopes.stream()
                .map(s -> new ScopeSummary(s, counts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    public ScopeWithBlocks getScope(Long userId, Long scopeId) {
        AvailabilityScope scope = scopeRepository.findByIdAndUserId(scopeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AVAILABILITY_NOT_FOUND));
        return new ScopeWithBlocks(scope,
                blockRepository.findByScopeIdOrderById(scopeId));
    }

    /**
     * 블록 전량 교체 — scope 행을 비관 잠금해 revision 검사와 교체를 직렬화한다.
     * 순서: 잠금·소유권(404) → revision(409) → 블록 검증(400) → 삭제+삽입 → 상태 전이·revision+1.
     */
    @Transactional
    public ScopeWithBlocks putBlocks(Long userId, Long scopeId, List<BlockDraft> drafts,
            boolean complete, int expectedRevision) {
        AvailabilityScope scope = scopeRepository.findByIdAndUserIdForUpdate(scopeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AVAILABILITY_NOT_FOUND));
        if (scope.getRevision() != expectedRevision) {
            throw new BusinessException(ErrorCode.AVAILABILITY_REVISION_CONFLICT);
        }
        List<AvailabilityBlock> blocks = drafts.stream()
                .map(d -> toBlock(scope, d))
                .toList();
        blockRepository.deleteByScopeId(scopeId);
        blockRepository.flush();
        blockRepository.saveAll(blocks);
        if (complete) {
            scope.complete();
        }
        scope.bumpRevision();
        return new ScopeWithBlocks(scope, blocks);
    }

    private AvailabilityBlock toBlock(AvailabilityScope scope, BlockDraft d) {
        if (d.kind() != BlockKind.valueOf(scope.getMode().name())) {
            throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
        }
        if (d.kind() == BlockKind.TIME) {
            // 종료 시각은 반개방 상한이라 범위 끝((rangeEnd+1일) 00:00)과 같아도 허용
            LocalDateTime boundary = scope.getRangeEnd().plusDays(1).atStartOfDay();
            if (d.startAt() == null || d.endAt() == null || !d.startAt().isBefore(d.endAt())
                    || !scope.covers(d.startAt()) || d.endAt().isAfter(boundary)) {
                throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
            }
            return AvailabilityBlock.time(scope.getId(), d.startAt(), d.endAt());
        }
        if (d.blockDate() == null || !scope.covers(d.blockDate())) {
            throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
        }
        return AvailabilityBlock.date(scope.getId(), d.blockDate());
    }

    @Transactional
    public void deleteScope(Long userId, Long scopeId) {
        AvailabilityScope scope = scopeRepository.findByIdAndUserIdForUpdate(scopeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AVAILABILITY_NOT_FOUND));
        blockRepository.deleteByScopeId(scopeId);
        scopeRepository.delete(scope);
    }

    public boolean getUseCalendarInference(Long userId) {
        return preferenceRepository.findById(userId)
                .map(AvailabilityPreference::isUseCalendarInference)
                .orElse(false);
    }

    @Transactional
    public boolean putUseCalendarInference(Long userId, boolean value) {
        // upsert 경합 — 동시 PUT이 둘 다 "행 없음"으로 보는 것을 user 잠금으로 직렬화한다.
        userRepository.findByIdForUpdate(userId);
        AvailabilityPreference pref = preferenceRepository.findById(userId)
                .orElseGet(() -> new AvailabilityPreference(userId, value));
        pref.setUseCalendarInference(value);
        preferenceRepository.save(pref);
        return value;
    }

    public List<PreferredWindow> getPreferredWindows(Long userId) {
        return preferredWindowRepository.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId);
    }

    /**
     * 선호 윈도우 전량 교체 — user 행을 잠가 동시 PUT을 직렬화한다.
     * 순서: 잠금 → 검증(400) → 삭제+삽입.
     */
    @Transactional
    public List<PreferredWindow> putPreferredWindows(Long userId,
            List<WindowDraft> drafts) {
        userRepository.findByIdForUpdate(userId);
        validatePreferredWindows(drafts);
        List<PreferredWindow> windows = drafts.stream()
                .map(d -> new PreferredWindow(userId, d.dayOfWeek(), d.startTime(),
                        d.endTime()))
                .toList();
        preferredWindowRepository.deleteByUserId(userId);
        preferredWindowRepository.flush();
        preferredWindowRepository.saveAll(windows);
        return windows.stream()
                .sorted(Comparator.comparingInt(PreferredWindow::getDayOfWeek)
                        .thenComparing(PreferredWindow::getStartTime))
                .toList();
    }

    /**
     * [s,e) 반개방 기준 같은 요일 내 중첩 거부 — 맞닿음(a.end == b.start)은 허용.
     * 요일별 startTime 정렬 후 인접 쌍만 비교하면 전체 중첩을 잡는다.
     */
    private void validatePreferredWindows(List<WindowDraft> drafts) {
        if (drafts.size() > MAX_PREFERRED_WINDOWS) {
            throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
        }
        Map<Integer, List<WindowDraft>> byDay = new HashMap<>();
        for (WindowDraft d : drafts) {
            if (d.dayOfWeek() < 1 || d.dayOfWeek() > 7 || d.startTime() == null
                    || d.endTime() == null || !d.startTime().isBefore(d.endTime())) {
                throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
            }
            byDay.computeIfAbsent(d.dayOfWeek(), k -> new ArrayList<>()).add(d);
        }
        for (List<WindowDraft> dayDrafts : byDay.values()) {
            List<WindowDraft> sorted = dayDrafts.stream()
                    .sorted(Comparator.comparing(WindowDraft::startTime))
                    .toList();
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).startTime().isBefore(sorted.get(i - 1).endTime())) {
                    throw new BusinessException(ErrorCode.AVAILABILITY_INVALID);
                }
            }
        }
    }

    public record ScopeWithBlocks(AvailabilityScope scope, List<AvailabilityBlock> blocks) {
    }

    public record ScopeSummary(AvailabilityScope scope, long blockCount) {
    }

    /** 블록 입력 — kind enum 검증은 컨트롤러 @Pattern이 끝낸 값만 들어온다. */
    public record BlockDraft(BlockKind kind, LocalDateTime startAt, LocalDateTime endAt,
            LocalDate blockDate) {
    }

    /** 선호 윈도우 입력 — dayOfWeek 범위·중첩 검증은 서비스가 한다. */
    public record WindowDraft(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
