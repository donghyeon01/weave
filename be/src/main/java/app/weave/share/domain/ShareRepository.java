package app.weave.share.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<Share, Long> {

    List<Share> findByOwnerIdOrderByIdDesc(Long ownerId);

    Optional<Share> findByOwnerIdAndItemTypeAndItemId(Long ownerId, ShareItemType itemType,
            Long itemId);

    /** 피드 후보 — 친구 소유 비공개 외 항목, id desc 커서. canView는 서비스에서 재검증한다. */
    List<Share> findByOwnerIdInAndVisibilityNotAndIdLessThanOrderByIdDesc(
            Collection<Long> ownerIds, ShareVisibility visibility, Long id);
}
