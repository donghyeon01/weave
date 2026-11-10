import { apiFetch } from '../../../lib/api-client'
import type { ChatMessage, ChatRoomSummary } from '../types'

// api-contract §3.10 — 채팅 REST 래퍼. STOMP 송수신은 lib/stomp이 담당한다.
export function fetchChatRooms() {
  return apiFetch<ChatRoomSummary[]>('/api/chat/rooms')
}

export function fetchChatMessages(roomId: number, cursor?: number, limit = 50) {
  const params = new URLSearchParams()
  if (cursor !== undefined) params.set('cursor', String(cursor))
  params.set('limit', String(limit))
  return apiFetch<ChatMessage[]>(`/api/chat/rooms/${roomId}/messages?${params}`)
}

export function postRoomRead(roomId: number, lastReadMessageId: number) {
  return apiFetch<void>(`/api/chat/rooms/${roomId}/read`, {
    method: 'POST',
    body: { lastReadMessageId },
  })
}
