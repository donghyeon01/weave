package app.weave.friend.domain;

import app.weave.friend.domain.Friendship.Status;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // (user_id=:me AND status=:s1) OR (friend_id=:me AND status=:s2) — And가 Or보다 우선한다.
    // findByUserIdOrFriendIdAndStatus 형태는 status 조건이 friend_id 쪽에만 걸려 회귀 버그였음 (plan T12)
    List<Friendship> findByUserIdAndStatusOrFriendIdAndStatus(
            Long me, Status s1, Long me2, Status s2);

    List<Friendship> findByFriendIdAndStatus(Long friendId, Status status);

    List<Friendship> findByUserIdAndStatus(Long userId, Status status);

    Optional<Friendship> findByUserIdAndFriendId(Long userId, Long friendId);

    Optional<Friendship> findByUserIdAndFriendIdAndStatus(Long userId, Long friendId,
            Status status);

    /** 수락·삭제의 참여자 검사 — me가 행의 어느 쪽이든 조회. */
    @Query("select f from Friendship f where f.id = :id and (f.userId = :me or f.friendId = :me)")
    Optional<Friendship> findByIdAndParticipant(@Param("id") Long id, @Param("me") Long me);

    /** FOR UPDATE 잠금 — accept/cancel 계열은 반드시 이 메서드로 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Friendship f where f.id = :id")
    Optional<Friendship> findByIdForUpdate(@Param("id") Long id);
}
