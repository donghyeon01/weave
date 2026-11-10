package app.weave.share.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareTargetRepository extends JpaRepository<ShareTarget, ShareTargetId> {

    List<ShareTarget> findByUserId(Long userId);

    List<ShareTarget> findByShareId(Long shareId);

    boolean existsByShareIdAndUserId(Long shareId, Long userId);

    Optional<ShareTarget> findByShareIdAndUserId(Long shareId, Long userId);

    void deleteByShareIdAndUserId(Long shareId, Long userId);

    void deleteByShareIdAndUserIdIn(Long shareId, Collection<Long> userIds);
}
