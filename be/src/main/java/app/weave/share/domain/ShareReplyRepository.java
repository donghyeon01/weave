package app.weave.share.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareReplyRepository extends JpaRepository<ShareReply, Long> {

    /** 공유별 살아있는 답글 수 집계 행 — 목록의 replyCount 원천 (GROUP BY 1회, N+1 방지). */
    interface ReplyCountRow {
        Long getShareId();
        Long getCnt();
    }

    long countByShareIdAndDeletedAtIsNull(Long shareId);

    Optional<ShareReply> findByIdAndShareId(Long id, Long shareId);

    /** 루트만 커서 페이지네이션 — id > cursor 오름차순, limit은 Pageable로 제한한다. */
    List<ShareReply> findByShareIdAndParentIdIsNullAndIdGreaterThanOrderByIdAsc(
            Long shareId, Long id, Pageable pageable);

    /** 루트 페이지 N건의 대댓글을 IN 1쿼리로 조회 — 루트별 루프 쿼리(N+1) 방지. */
    List<ShareReply> findByParentIdInOrderByIdAsc(List<Long> parentIds);

    /** 공유 N개의 살아있는 답글 수를 GROUP BY 한 번으로 집계한다. */
    @Query("select r.shareId as shareId, count(r.id) as cnt from ShareReply r "
            + "where r.shareId in :shareIds and r.deletedAt is null group by r.shareId")
    List<ReplyCountRow> countByShareIds(@Param("shareIds") List<Long> shareIds);
}
