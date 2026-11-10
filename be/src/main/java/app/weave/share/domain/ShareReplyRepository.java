package app.weave.share.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareReplyRepository extends JpaRepository<ShareReply, Long> {

    long countByShareIdAndDeletedAtIsNull(Long shareId);

    Optional<ShareReply> findByIdAndShareId(Long id, Long shareId);
}
