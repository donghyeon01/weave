package app.weave.share.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<Share, Long> {

    List<Share> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    boolean existsByOwnerIdAndItemTypeAndItemId(Long ownerId, ShareItemType itemType,
            Long itemId);
}
