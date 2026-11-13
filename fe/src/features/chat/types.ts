// api-contract §3.10 — 채팅방 목록·메시지 DTO.
// Slot은 scheduling 계약과 동형 — feature 간 import 금지라 로컬로 둔다.
export type Slot =
  | { type: 'TIME'; date: string; startTime: string; endTime: string }
  | { type: 'DATE'; startDate: string; endDate: string }

export type RoomStatus = 'OPEN' | 'CLOSED'

export interface ChatRoomSummary {
  roomId: number
  proposalId: number
  title: string
  slot: Slot
  unreadCount: number
  status: RoomStatus
  // 목록 카드 프리뷰·메타 라인 — 메시지 없는 방은 lastMessage·lastMessageAt이 null (계약 §3.10)
  lastMessage: string | null
  lastMessageAt: string | null
  memberCount: number
  /** 시리즈 조율의 총 회차 수 — 단일 제안 방은 1 (계약 §3.10, V2 scheduling 확장) */
  occurrenceCount: number
}

export interface ChatMessage {
  id: number
  senderId: number
  nickname: string
  content: string
  createdAt: string
}

// SEND /app/rooms/{id} 페이로드 — content 1~2000, clientMessageId ≤64 (계약 §3.10)
export interface ChatSendPayload {
  content: string
  clientMessageId: string
}
