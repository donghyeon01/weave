package app.weave.friend.service;

import app.weave.auth.domain.User;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.BusyBlock;
import app.weave.common.port.EventAccess;
import app.weave.common.port.EventRef;
import app.weave.common.port.ProposalAccess;
import app.weave.common.port.ProposalRoster;
import app.weave.friend.domain.Friendship;
import app.weave.friend.domain.Friendship.Status;
import app.weave.friend.domain.FriendshipRepository;
import app.weave.notification.service.NotificationPayload;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.notification.service.NotificationPublisher;
import app.weave.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

    /** friendship 행 + 상대방 표시 정보 + 다음 약속 — 컨트롤러가 FriendshipResponse로 변환한다. */
    public record FriendView(Friendship friendship, User counterparty, EventRef nextEvent) {
    }

    private final FriendshipRepository repository;
    private final UserService userService;
    private final NotificationPublisher publisher;
    private final EventAccess eventAccess;
    private final ProposalAccess proposalAccess;

    public FriendService(FriendshipRepository repository, UserService userService,
            NotificationPublisher publisher, EventAccess eventAccess,
            ProposalAccess proposalAccess) {
        this.repository = repository;
        this.userService = userService;
        this.publisher = publisher;
        this.eventAccess = eventAccess;
        this.proposalAccess = proposalAccess;
    }

    @Transactional
    public FriendView request(Long me, String email, String message) {
        User target = userService.findByEmailStrict(email);
        if (target.getId().equals(me)) {
            throw new BusinessException(ErrorCode.FRIEND_SELF_REQUEST);
        }
        // 어느 방향이든 기존 행이 있으면 신규 요청 불가
        Optional<Friendship> existing = repository.findByUserIdAndFriendId(me, target.getId())
                .or(() -> repository.findByUserIdAndFriendId(target.getId(), me));
        if (existing.isPresent()) {
            throw new BusinessException(existing.get().getStatus() == Status.ACCEPTED
                    ? ErrorCode.FRIEND_ALREADY : ErrorCode.FRIEND_DUPLICATE_REQUEST);
        }
        Friendship saved = repository.save(new Friendship(me, target.getId(), message));
        User sender = userService.getMe(me);
        publisher.publish(target.getId(), new NotificationPayload.FriendRequest(
                saved.getId(), new UserRef(me, sender.getNickname())));
        return new FriendView(saved, target, null);
    }

    /**
     * 수락 — db-schema §4 트랜잭션1: FOR UPDATE 잠금 후 status=ACCEPTED + 역방향 PENDING 정리.
     * 잠금은 friendship id 오름차순으로 획득해 상호 수락 데드락을 방지한다 (plan T12 codex #1).
     */
    @Transactional
    public FriendView accept(Long me, Long id) {
        Friendship target = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        Optional<Friendship> reverse = repository.findByUserIdAndFriendIdAndStatus(
                target.getFriendId(), target.getUserId(), Status.PENDING);

        List<Long> lockIds = Stream.concat(Stream.of(id),
                reverse.stream().map(Friendship::getId)).sorted().toList();
        Map<Long, Friendship> locked = new HashMap<>();
        for (Long lockId : lockIds) {
            repository.findByIdForUpdate(lockId).ifPresent(f -> locked.put(lockId, f));
        }

        Friendship lockedTarget = locked.get(id);
        if (lockedTarget == null) {
            throw new BusinessException(ErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        // 수신자(friend_id)만 수락 가능 — 발신자는 403, 제3자는 존재 비노출 404
        if (!lockedTarget.getFriendId().equals(me)) {
            throw new BusinessException(lockedTarget.getUserId().equals(me)
                    ? ErrorCode.ACCESS_DENIED : ErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        if (lockedTarget.getStatus() == Status.ACCEPTED) {
            throw new BusinessException(ErrorCode.FRIEND_ALREADY);
        }

        lockedTarget.accept();
        Friendship lockedReverse = reverse.map(Friendship::getId).map(locked::get).orElse(null);
        if (lockedReverse != null && lockedReverse.getStatus() == Status.PENDING) {
            repository.delete(lockedReverse);
        }

        User accepter = userService.getMe(me);
        publisher.publish(lockedTarget.getUserId(), new NotificationPayload.FriendAccepted(
                lockedTarget.getId(), new UserRef(me, accepter.getNickname())));
        // 수락으로 ACCEPTED 전환 — 재요청으로 이전 공유 일정이 남아 있을 수 있어 nextEvent를 계산한다
        EventRef next = nextEventsOf(me, List.of(lockedTarget)).get(lockedTarget.getId());
        return new FriendView(lockedTarget, accepter, next);
    }

    @Transactional
    public void delete(Long me, Long id) {
        Friendship friendship = repository.findByIdAndParticipant(id, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        repository.delete(friendship);
    }

    /**
     * 친구 일정 미리보기 — ACCEPTED 관계만, 조율 불가 블록(시작·종료)만 반환한다 (계약 §3.6).
     * {id}는 friendship 행 id — 비친구·PENDING·타인 관계는 존재 비노출 404.
     */
    @Transactional(readOnly = true)
    public List<BusyBlock> schedule(Long me, Long friendshipId, LocalDate start, LocalDate end) {
        Friendship friendship = repository.findByIdAndParticipant(friendshipId, me)
                .filter(f -> f.getStatus() == Status.ACCEPTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        return eventAccess.busyBlocksOf(friendship.counterpartyOf(me), start.atStartOfDay(),
                end.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<FriendView> listFriends(Long me) {
        List<Friendship> rows = repository.findAcceptedFriendships(me);
        Map<Long, EventRef> nextEvents = nextEventsOf(me, rows);
        Map<Long, User> users = userService.findAllByIds(
                rows.stream().map(f -> f.counterpartyOf(me)).toList());
        return rows.stream()
                .map(f -> new FriendView(f, users.get(f.counterpartyOf(me)),
                        nextEvents.get(f.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendView> listReceived(Long me) {
        return toViews(repository.findByFriendIdAndStatus(me, Status.PENDING), me);
    }

    @Transactional(readOnly = true)
    public List<FriendView> listSent(Long me) {
        return toViews(repository.findByUserIdAndStatus(me, Status.PENDING), me);
    }

    /** ACCEPTED 여부 양방향 판정 — share 가시성 검증 원천 (행 존재만으론 통과 금지). */
    @Transactional(readOnly = true)
    public boolean isFriend(Long me, Long other) {
        return repository.findByUserIdAndFriendIdAndStatus(me, other, Status.ACCEPTED)
                .isPresent()
                || repository.findByUserIdAndFriendIdAndStatus(other, me, Status.ACCEPTED)
                .isPresent();
    }

    /** ACCEPTED 상대 id 집합 — share 피드 후보 필터·그룹 멤버의 친구 검증용. */
    @Transactional(readOnly = true)
    public Set<Long> acceptedFriendIds(Long me) {
        return repository.findAcceptedFriendships(me).stream()
                .map(f -> f.counterpartyOf(me))
                .collect(Collectors.toSet());
    }

    private List<FriendView> toViews(List<Friendship> rows, Long me) {
        Map<Long, User> users = userService.findAllByIds(
                rows.stream().map(f -> f.counterpartyOf(me)).toList());
        // PENDING 요청에는 함께 확정된 일정이 성립하지 않아 nextEvent는 항상 null
        return rows.stream()
                .map(f -> new FriendView(f, users.get(f.counterpartyOf(me)), null))
                .toList();
    }

    /**
     * 다음 약속 — 내 proposal 출처 미래 일정 중 원 제안 로스터에 그 친구가 포함된 가장 이른 1건.
     * events·제안 데이터는 상류 모듈이 볼 수 없어 EventAccess·ProposalAccess 포트로 조회 (계약 §3.6).
     */
    private Map<Long, EventRef> nextEventsOf(Long me, List<Friendship> friendships) {
        if (friendships.isEmpty()) {
            return Map.of();
        }
        List<EventRef> future = eventAccess.proposalEventsAfter(me, LocalDateTime.now());
        Map<Long, ProposalRoster> rosters = proposalAccess.rosters(future.stream()
                .map(EventRef::proposalId).filter(p -> p != null).distinct().toList());
        Map<Long, EventRef> result = new HashMap<>();
        for (Friendship f : friendships) {
            Long friend = f.counterpartyOf(me);
            future.stream()
                    .filter(e -> {
                        ProposalRoster r = e.proposalId() != null
                                ? rosters.get(e.proposalId()) : null;
                        return r != null && r.involves(friend);
                    })
                    .findFirst() // future는 startTime 오름차순 — 첫 일치가 가장 이른 약속
                    .ifPresent(e -> result.put(f.getId(), e));
        }
        return result;
    }
}
