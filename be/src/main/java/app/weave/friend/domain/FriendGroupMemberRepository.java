package app.weave.friend.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendGroupMemberRepository
        extends JpaRepository<FriendGroupMember, FriendGroupMemberId> {

    List<FriendGroupMember> findByGroupId(Long groupId);

    List<FriendGroupMember> findByGroupIdIn(Collection<Long> groupIds);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    void deleteByGroupId(Long groupId);
}
