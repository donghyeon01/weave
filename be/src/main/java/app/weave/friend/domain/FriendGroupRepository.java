package app.weave.friend.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendGroupRepository extends JpaRepository<FriendGroup, Long> {

    List<FriendGroup> findByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<FriendGroup> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);
}
