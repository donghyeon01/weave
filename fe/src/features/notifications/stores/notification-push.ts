import { queryClient } from '../../../lib/react-query'
import { onStompConnect, subscribeTopic } from '../../../lib/stomp'
import type { StompMessage } from '../../../lib/stomp'
import type { NotificationItem } from '../types'

// api-contract §3.9 — notification 구독은 /user/queue/notifications.
// 재연결 성공 시 REST 목록을 먼저 재조회해 유실 push를 복원한다 (목록이 원천, push는 보조).

const TYPE_INVALIDATIONS: Record<string, string[][]> = {
  FRIEND_REQUEST: [['notifications'], ['friends']],
  FRIEND_ACCEPTED: [['notifications'], ['friends']],
  // PROPOSAL_*는 채팅방 생명주기와 연동된다 (생성→OPEN, 확정·거절→CLOSED) —
  // ['chat','rooms']를 빼면 방 목록·뱃지·종료 상태가 갱신되지 않는다.
  PROPOSAL_RECEIVED: [['notifications'], ['scheduling', 'proposals'], ['chat', 'rooms']],
  PROPOSAL_ALL_ACCEPTED: [['notifications'], ['scheduling', 'proposals']],
  PROPOSAL_CONFIRMED: [
    ['notifications'],
    ['scheduling', 'proposals'],
    ['events'],
    ['chat', 'rooms'],
  ],
  PROPOSAL_DECLINED: [['notifications'], ['scheduling', 'proposals'], ['chat', 'rooms']],
  // 구독 친구의 신규 공개 — 피드·프로필 항목 목록(share)과 친구 화면 카운트를 갱신한다.
  SHARED_ITEM: [['notifications'], ['share'], ['friends']],
  SHARE_REPLY: [['notifications'], ['share']],
}

// 단절 중 어떤 타입의 push를 놓쳤는지 알 수 없으므로, 재연결 시에는
// TYPE_INVALIDATIONS의 무효화 대상 전체(유니온)를 복구한다 — 목록이 원천, push는 보조.
const RECONNECT_KEYS = Array.from(
  new Map(Object.values(TYPE_INVALIDATIONS).flat().map((k) => [k.join('\x00'), k])).values(),
)

function onNotification(message: StompMessage) {
  try {
    const item = JSON.parse(message.body) as NotificationItem
    for (const key of TYPE_INVALIDATIONS[item.type] ?? [['notifications']]) {
      queryClient.invalidateQueries({ queryKey: key })
    }
  } catch {
    queryClient.invalidateQueries({ queryKey: ['notifications'] })
  }
}

let registered = false

// app-shell의 STOMP 시작 시점에 1회 등록 — 구독·재연결 재조회는 lib/stomp이 상태를 관리한다.
export function registerNotificationPush() {
  if (registered) return
  registered = true
  subscribeTopic('/user/queue/notifications', onNotification)
  onStompConnect(() => {
    for (const key of RECONNECT_KEYS) {
      queryClient.invalidateQueries({ queryKey: key })
    }
  })
}
