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

    // ACCEPTED 양방향 조회 — OR 단일 쿼리는 (user_id·friend_id) 양쪽 인덱스를 못 타
    // 풀스캔(type=ALL)이 된다. EXPLAIN 실측 후 인덱스를 타는 2쿼리 UNION으로 분리 (plan T16).
    default List<Friendship> findAcceptedFriendships(Long me) {
        List<Friendship> rows = new java.util.ArrayList<>(
                findByUserIdAndStatus(me, Status.ACCEPTED));
        rows.addAll(findByFriendIdAndStatus(me, Status.ACCEPTED));
        return rows;
    }

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
