import { Client } from '@stomp/stompjs'
import { env } from '../../../config/env'
import { ensureFreshToken } from '../../../lib/refresh'
import { queryClient } from '../../../lib/react-query'
import { useAuthStore } from '../../../stores/auth-store'
import type { NotificationItem } from '../types'

// api-contract §3.9 — raw WebSocket /ws + STOMP, 구독은 /user/queue/notifications.
// CONNECT 전 ensureFreshToken()으로 만료 토큰을 갱신한다 — HTTP 401과 같은 in-flight를 공유.
// 재연결 성공 시 REST 목록을 먼저 재조회해 유실 push를 복원한다 (목록이 원천, push는 보조).

let client: Client | null = null

const TYPE_INVALIDATIONS: Record<string, string[][]> = {
  FRIEND_REQUEST: [['notifications'], ['friends']],
  FRIEND_ACCEPTED: [['notifications'], ['friends']],
  PROPOSAL_RECEIVED: [['notifications'], ['scheduling', 'proposals']],
  PROPOSAL_CONFIRMED: [['notifications'], ['scheduling', 'proposals'], ['events']],
  PROPOSAL_DECLINED: [['notifications'], ['scheduling', 'proposals']],
}

function onNotification(message: { body: string }) {
  try {
    const item = JSON.parse(message.body) as NotificationItem
    for (const key of TYPE_INVALIDATIONS[item.type] ?? [['notifications']]) {
      queryClient.invalidateQueries({ queryKey: key })
    }
  } catch {
    queryClient.invalidateQueries({ queryKey: ['notifications'] })
  }
}

async function connect(): Promise<Client | null> {
  const token = await ensureFreshToken()
  if (token === null) return null

  const c = new Client({
    // 배포는 apiBaseUrl의 API 도메인, 로컬은 vite dev 프록시가 /ws를 백엔드 8081로 넘긴다
    brokerURL: `${(env.apiBaseUrl || location.origin).replace(/^http/, 'ws')}/ws`,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      // 재연결 포함 — 유실 push 복원을 위해 REST 목록 먼저 재조회
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      c.subscribe('/user/queue/notifications', onNotification)
    },
    onStompError: (frame) => {
      // ERROR 프레임(대개 토큰 만료) — 토큰 갱신 후 1회 재연결 시도는 reconnectDelay가 커버한다
      void frame
    },
  })
  c.activate()
  return c
}

export function startStompClient() {
  if (useAuthStore.getState().status !== 'authed' || client !== null) return
  void connect().then((c) => {
    // activate는 비동기 — 그 사이 logout되면 바로 해제해 좀비 연결을 막는다
    if (useAuthStore.getState().status !== 'authed') {
      c?.deactivate()
      return
    }
    client = c
  })
}

export function stopStompClient() {
  void client?.deactivate()
  client = null
}
