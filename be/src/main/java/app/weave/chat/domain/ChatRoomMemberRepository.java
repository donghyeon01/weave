package app.weave.chat.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatRoomMember> findByRoomId(Long roomId);

    List<ChatRoomMember> findByUserId(Long userId);
}
