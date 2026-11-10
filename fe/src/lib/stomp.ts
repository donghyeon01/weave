import { Client } from '@stomp/stompjs'
import type { StompSubscription } from '@stomp/stompjs'
import { env } from '../config/env'
import { ensureFreshToken } from './refresh'
import { useAuthStore } from '../stores/auth-store'

// api-contract §3.9 — raw WebSocket /ws + STOMP.
// fe 규칙상 feature 간 import 금지 — chat(Task 8)이 같은 연결로 /topic/rooms/{id}를
// 구독하므로 싱글턴을 lib에 둔다. feature별 구독·무효화 로직은 각 feature가
// subscribeTopic/onStompConnect로 등록한다.
// 토큰은 beforeConnect에서 매 연결 시도마다 갱신한다 — 최초 CONNECT뿐 아니라
// reconnectDelay 재연결에도 최신 accessToken이 들어가야 만료 후 재연결이 산다.

export interface StompMessage {
  body: string
}

interface DesiredSub {
  destination: string
  onMessage: (msg: StompMessage) => void
  live?: StompSubscription
}

let client: Client | null = null
const subscriptions: DesiredSub[] = []
const connectListeners = new Set<() => void>()

/**
 * destination 구독. 미연결이면 다음 onConnect에서 자동 구독된다.
 * 반환된 해제 함수는 재구독 목록과 현재 구독 양쪽에서 제거한다 — 호출부가
 * 컴포넌트 unmount 정리에 쓰지 않으면 비멤버 목적지가 재연결마다 재구독되어
 * 서버 거부(ERROR+세션 종료)가 무한 반복된다.
 */
export function subscribeTopic(destination: string, onMessage: (msg: StompMessage) => void) {
  const sub: DesiredSub = { destination, onMessage }
  subscriptions.push(sub)
  if (client !== null && client.connected) {
    sub.live = client.subscribe(destination, onMessage)
  }
  return () => {
    const i = subscriptions.indexOf(sub)
    if (i !== -1) subscriptions.splice(i, 1)
    sub.live?.unsubscribe()
    sub.live = undefined
  }
}

/** 매 연결(재연결 포함) 때마다 호출 — 유실 push 복원용 REST 재조회 등에 사용. */
export function onStompConnect(listener: () => void) {
  connectListeners.add(listener)
}

/** STOMP SEND — 미연결이면 throw해 호출부가 사용자에게 실패를 표면화하게 한다. */
export function publish(destination: string, body: unknown) {
  if (client === null || !client.connected) {
    throw new Error('STOMP not connected')
  }
  client.publish({ destination, body: JSON.stringify(body) })
}

// stompjs는 재연결마다 구독 테이블을 초기화하므로 onConnect 때마다 전부 재구독한다.
// 같은 연결에서 이 함수는 onConnect당 1회만 불리므로 중복 subscribe는 없다.
function applySubscriptions(c: Client) {
  for (const s of subscriptions) {
    s.live = c.subscribe(s.destination, s.onMessage)
  }
}

export function startStompClient() {
  if (useAuthStore.getState().status !== 'authed' || client !== null) return

  const c = new Client({
    // 배포는 apiBaseUrl의 API 도메인, 로컬은 vite dev 프록시가 /ws를 백엔드 8081로 넘긴다
    brokerURL: `${(env.apiBaseUrl || location.origin).replace(/^http/, 'ws')}/ws`,
    reconnectDelay: 5000,
    // 매 연결 시도마다 실행되는 훅 — 토큰 갱신에 실패하면 throw로 그 시도를 중단하고
    // reconnectDelay 재시도에 맡긴다 (네트워크 단절 시 자기 회복).
    beforeConnect: async () => {
      const token = await ensureFreshToken()
      if (token === null) throw new Error('STOMP connect aborted: no access token')
      c.connectHeaders = { Authorization: `Bearer ${token}` }
    },
    onConnect: () => {
      applySubscriptions(c)
      for (const listener of connectListeners) listener()
    },
    onStompError: (frame) => {
      // ERROR 프레임(대개 토큰 만료) — 다음 재시도는 beforeConnect가 최신 토큰을 넣는다
      void frame
    },
  })
  // activate 전에 동기 할당 — ensureFreshToken 대기 구간에 client가 null이면
  // StrictMode 시작→정리→시작이나 로그아웃이 와도 stopStompClient가 잡지 못해
  // 좀비 연결이 남는다. deactivate는 activate 진행 중에도 안전하다.
  client = c
  c.activate()
}

export function stopStompClient() {
  const c = client
  client = null
  void c?.deactivate()
}
