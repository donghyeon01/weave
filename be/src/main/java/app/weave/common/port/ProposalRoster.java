package app.weave.common.port;

import java.util.Set;

/** 제안의 주최자 + 참여자 id 집합 — 참여 친구 표시·nextEvent 멤버십 판정용. */
public record ProposalRoster(Long organizerId, Set<Long> participantUserIds) {

    /** 주최자 또는 참여자에 포함되는지. */
    public boolean involves(Long userId) {
        return organizerId.equals(userId) || participantUserIds.contains(userId);
    }
}
