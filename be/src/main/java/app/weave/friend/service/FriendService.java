package app.weave.friend.service;

import app.weave.auth.domain.User;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.friend.domain.Friendship;
import app.weave.friend.domain.Friendship.Status;
import app.weave.friend.domain.FriendshipRepository;
import app.weave.notification.service.NotificationPayload;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.notification.service.NotificationPublisher;
import app.weave.user.service.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

    /** friendship 행 + 상대방 표시 정보 — 컨트롤러가 FriendshipResponse로 변환한다. */
    public record FriendView(Friendship friendship, User counterparty) {
    }

    private final FriendshipRepository repository;
    private final UserService userService;
    private final NotificationPublisher publisher;

    public FriendService(FriendshipRepository repository, UserService userService,
            NotificationPublisher publisher) {
        this.repository = repository;
        this.userService = userService;
        this.publisher = publisher;
    }

    @Transactional
    public FriendView request(Long me, String email) {
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
        Friendship saved = repository.save(new Friendship(me, target.getId()));
        User sender = userService.getMe(me);
        publisher.publish(target.getId(), new NotificationPayload.FriendRequest(
                saved.getId(), new UserRef(me, sender.getNickname())));
        return new FriendView(saved, target);
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
        return new FriendView(lockedTarget, accepter);
    }

    @Transactional
    public void delete(Long me, Long id) {
        Friendship friendship = repository.findByIdAndParticipant(id, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        repository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendView> listFriends(Long me) {
        return toViews(repository.findAcceptedFriendships(me), me);
    }

    @Transactional(readOnly = true)
    public List<FriendView> listReceived(Long me) {
        return toViews(repository.findByFriendIdAndStatus(me, Status.PENDING), me);
    }

    @Transactional(readOnly = true)
    public List<FriendView> listSent(Long me) {
        return toViews(repository.findByUserIdAndStatus(me, Status.PENDING), me);
    }

    private List<FriendView> toViews(List<Friendship> rows, Long me) {
        Map<Long, User> users = userService.findAllByIds(
                rows.stream().map(f -> f.counterpartyOf(me)).toList());
        return rows.stream()
                .map(f -> new FriendView(f, users.get(f.counterpartyOf(me))))
                .toList();
    }
}
