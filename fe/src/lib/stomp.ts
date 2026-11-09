import { Client } from '@stomp/stompjs'
import { env } from '../config/env'
import { ensureFreshToken } from './refresh'
import { useAuthStore } from '../stores/auth-store'

// api-contract §3.9 — raw WebSocket /ws + STOMP.
// fe 규칙상 feature 간 import 금지 — chat(Task 8)이 같은 연결로 /topic/rooms/{id}를
// 구독하므로 싱글턴을 lib에 둔다. feature별 구독·무효화 로직은 각 feature가
// subscribeTopic/onStompConnect로 등록한다.
// CONNECT 전 ensureFreshToken()으로 만료 토큰을 갱신한다 — HTTP 401과 같은 in-flight를 공유.

export interface StompMessage {
  body: string
}

interface DesiredSub {
  destination: string
  onMessage: (msg: StompMessage) => void
  // 재연결 시 stompjs가 기존 구독을 자동 복원하므로 "이 Client 인스턴스에
  // 이미 subscribe했는가"만 추적한다 — onConnect에서 중복 구독 방지용.
  appliedTo: Client | null
}

let client: Client | null = null
const subscriptions: DesiredSub[] = []
const connectListeners = new Set<() => void>()

/** destination 구독. 미연결이면 다음 onConnect에서 자동 구독된다. */
export function subscribeTopic(destination: string, onMessage: (msg: StompMessage) => void) {
  const sub: DesiredSub = { destination, onMessage, appliedTo: null }
  subscriptions.push(sub)
  if (client !== null && client.connected) {
    client.subscribe(destination, onMessage)
    sub.appliedTo = client
  }
}

/** 매 연결(재연결 포함) 때마다 호출 — 유실 push 복원용 REST 재조회 등에 사용. */
export function onStompConnect(listener: () => void) {
  connectListeners.add(listener)
}

function applySubscriptions(c: Client) {
  for (const s of subscriptions) {
    if (s.appliedTo === c) continue
    c.subscribe(s.destination, s.onMessage)
    s.appliedTo = c
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
      applySubscriptions(c)
      for (const listener of connectListeners) listener()
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
