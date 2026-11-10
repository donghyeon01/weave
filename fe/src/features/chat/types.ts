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
  // 계약 확장 중인 필드 — 응답에 있으면 목록 카드·메타 라인에 반영한다.
  lastMessage?: string
  lastMessageAt?: string
  memberCount?: number
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
