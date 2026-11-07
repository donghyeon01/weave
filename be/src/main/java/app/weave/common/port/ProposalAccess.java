package app.weave.common.port;

import java.util.Collection;
import java.util.Map;

/**
 * scheduling 모듈이 소유한 제안 로스터의 읽기 전용 포트.
 * scheduling을 볼 수 없는 상류 모듈(event·friend)이 참여자 조합을 이 포트로 조회한다 —
 * 구현은 scheduling 패키지의 어댑터가 담당 (capability-map 조회 포트 규약).
 */
public interface ProposalAccess {

    /** 제안 id → 주최자·참여자 집합. 없는 id는 결과에서 생략. */
    Map<Long, ProposalRoster> rosters(Collection<Long> proposalIds);
}
