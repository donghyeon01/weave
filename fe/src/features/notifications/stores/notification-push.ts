import { queryClient } from '../../../lib/react-query'
import { onStompConnect, subscribeTopic } from '../../../lib/stomp'
import type { StompMessage } from '../../../lib/stomp'
import type { NotificationItem } from '../types'

// api-contract §3.9 — notification 구독은 /user/queue/notifications.
// 재연결 성공 시 REST 목록을 먼저 재조회해 유실 push를 복원한다 (목록이 원천, push는 보조).

const TYPE_INVALIDATIONS: Record<string, string[][]> = {
  FRIEND_REQUEST: [['notifications'], ['friends']],
  FRIEND_ACCEPTED: [['notifications'], ['friends']],
  PROPOSAL_RECEIVED: [['notifications'], ['scheduling', 'proposals']],
  PROPOSAL_ALL_ACCEPTED: [['notifications'], ['scheduling', 'proposals']],
  PROPOSAL_CONFIRMED: [['notifications'], ['scheduling', 'proposals'], ['events']],
  PROPOSAL_DECLINED: [['notifications'], ['scheduling', 'proposals']],
}

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
    queryClient.invalidateQueries({ queryKey: ['notifications'] })
  })
}
