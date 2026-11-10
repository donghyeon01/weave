package app.weave.share.service;

import app.weave.auth.domain.User;
import app.weave.auth.domain.UserRepository;
import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.common.port.DiaryAccess;
import app.weave.common.port.EventAccess;
import app.weave.common.port.SharedDiary;
import app.weave.common.port.SharedEvent;
import app.weave.notification.service.NotificationPayload;
import app.weave.notification.service.NotificationPayload.UserRef;
import app.weave.notification.service.NotificationPublisher;
import app.weave.share.domain.Share;
import app.weave.share.domain.ShareReply;
import app.weave.share.domain.ShareReplyRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 답글 스레드 — 2단 상한(부모는 루트만), 삭제는 tombstone으로 자식을 보존한다.
 * 열람자 검증은 ShareService.requireAudience에 위임 — 대상 지연 정리 정책도 그대로 탄다.
 */
@Service
public class ReplyService {

    private static final int MAX_LIMIT = 50;
    private static final int MAX_CONTENT = 500;

    /**
     * 답글 노드 — children은 루트에 임베드되는 대댓글(2단 상한이라 자식의 children은 항상 빈 리스트).
     * deleted=true(tombstone)이면 content는 ""로 마스킹한다.
     */
    public record ReplyNode(Long id, Long authorId, String nickname, String content,
            LocalDateTime createdAt, LocalDateTime updatedAt, boolean deleted,
            List<ReplyNode> children) {
    }

    private final ShareReplyRepository replyRepository;
    private final ShareService shareService;
    private final NotificationPublisher publisher;
    private final UserRepository userRepository;
    private final DiaryAccess diaryAccess;
    private final EventAccess eventAccess;

    public ReplyService(ShareReplyRepository replyRepository, ShareService shareService,
            NotificationPublisher publisher, UserRepository userRepository,
            DiaryAccess diaryAccess, EventAccess eventAccess) {
        this.replyRepository = replyRepository;
        this.shareService = shareService;
        this.publisher = publisher;
        this.userRepository = userRepository;
        this.diaryAccess = diaryAccess;
        this.eventAccess = eventAccess;
    }

    /**
     * 스레드 조회 — 루트만 id > cursor 오름차순 페이지네이션, 대댓글은 각 루트에 임베드.
     * 루트 N건의 자식은 IN 1쿼리로 가져온다 (N+1 방지).
     */
    @Transactional(readOnly = true)
    public List<ReplyNode> list(Long me, Long shareId, Long cursor, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        shareService.requireAudience(me, shareId);

        List<ShareReply> roots = replyRepository
                .findByShareIdAndParentIdIsNullAndIdGreaterThanOrderByIdAsc(shareId,
                        cursor == null ? 0L : cursor, PageRequest.of(0, limit));
        if (roots.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ShareReply>> childrenByParent = replyRepository
                .findByParentIdInOrderByIdAsc(roots.stream().map(ShareReply::getId).toList())
                .stream().collect(Collectors.groupingBy(ShareReply::getParentId));

        List<ShareReply> all = new ArrayList<>(roots);
        childrenByParent.values().forEach(all::addAll);
        Map<Long, String> nicknames = nicknamesOf(all);

        return roots.stream()
                .map(root -> toNode(root, nicknames,
                        childrenByParent.getOrDefault(root.getId(), List.of()).stream()
                                .map(child -> toNode(child, nicknames, List.of()))
                                .toList()))
                .toList();
    }

    /**
     * 작성 — 열람자만. parentId 지정 시 같은 share의 루트·비tombstone만 부모 가능(2단 상한).
     * 작성자가 공유 소유자가 아니면 소유자에게 SHARE_REPLY 알림을 발행한다.
     */
    @Transactional
    public ShareReply create(Long me, Long shareId, String content, Long parentId) {
        Share share = shareService.requireAudience(me, shareId);
        validateContent(content);
        if (parentId != null) {
            ShareReply parent = replyRepository.findByIdAndShareId(parentId, shareId)
                    .filter(p -> p.getParentId() == null && p.getDeletedAt() == null)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REPLY_PARENT_INVALID));
            parentId = parent.getId();
        }
        ShareReply saved = replyRepository.save(
                new ShareReply(shareId, parentId, me, content));

        if (!share.getOwnerId().equals(me)) {
            publisher.publish(share.getOwnerId(), new NotificationPayload.ShareReply(
                    shareId, saved.getId(), share.getItemType().name(), authorRef(me),
                    itemTitle(share)));
        }
        return saved;
    }

    /** 수정 — 작성자만. tombstone은 수정 불가(REPLY_NOT_FOUND), 타인은 REPLY_NOT_AUTHOR(404). */
    @Transactional
    public ShareReply update(Long me, Long shareId, Long replyId, String content) {
        shareService.requireAudience(me, shareId);
        ShareReply reply = replyRepository.findByIdAndShareId(replyId, shareId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPLY_NOT_FOUND));
        if (!reply.getAuthorId().equals(me)) {
            // 소유권 규약상 권한 부재도 404 — 답글 존재를 유출하지 않는다
            throw new BusinessException(ErrorCode.REPLY_NOT_AUTHOR);
        }
        validateContent(content);
        reply.updateContent(content);
        return reply;
    }

    /** 삭제 — 작성자 또는 공유 소유자. tombstone(deleted_at)으로 자식 스레드를 보존한다. */
    @Transactional
    public void delete(Long me, Long shareId, Long replyId) {
        Share share = shareService.requireAudience(me, shareId);
        ShareReply reply = replyRepository.findByIdAndShareId(replyId, shareId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPLY_NOT_FOUND));
        if (!reply.getAuthorId().equals(me) && !share.getOwnerId().equals(me)) {
            throw new BusinessException(ErrorCode.REPLY_NOT_AUTHOR);
        }
        reply.markDeleted();
    }

    /** 단건 ReplyNode 변환 — POST/PATCH 응답용. 루트면 children을 함께 임베드한다. */
    @Transactional(readOnly = true)
    public ReplyNode nodeOf(ShareReply reply) {
        List<ShareReply> children = reply.getParentId() == null
                ? replyRepository.findByParentIdInOrderByIdAsc(List.of(reply.getId()))
                : List.of();
        List<ShareReply> all = new ArrayList<>(children);
        all.add(reply);
        Map<Long, String> nicknames = nicknamesOf(all);
        return toNode(reply, nicknames, children.stream()
                .map(child -> toNode(child, nicknames, List.of())).toList());
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.REPLY_INVALID);
        }
    }

    private ReplyNode toNode(ShareReply reply, Map<Long, String> nicknames,
            List<ReplyNode> children) {
        boolean deleted = reply.getDeletedAt() != null;
        return new ReplyNode(reply.getId(), reply.getAuthorId(),
                nicknames.get(reply.getAuthorId()), deleted ? "" : reply.getContent(),
                reply.getCreatedAt(), reply.getUpdatedAt(), deleted, children);
    }

    private Map<Long, String> nicknamesOf(List<ShareReply> replies) {
        List<Long> authorIds = replies.stream().map(ShareReply::getAuthorId).distinct()
                .toList();
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    private UserRef authorRef(Long authorId) {
        String nickname = userRepository.findById(authorId)
                .map(User::getNickname).orElse(null);
        return new UserRef(authorId, nickname);
    }

    /** 알림 페이로드의 itemTitle — 원본 삭제 시 "" (알림이 항목 존재를 유출하지 않도록). */
    private String itemTitle(Share share) {
        return switch (share.getItemType()) {
            case DIARY -> diaryAccess.diaryOf(share.getOwnerId(), share.getItemId())
                    .map(SharedDiary::title).orElse("");
            case EVENT -> eventAccess.eventDetailOf(share.getOwnerId(), share.getItemId())
                    .map(SharedEvent::title).orElse("");
        };
    }
}
