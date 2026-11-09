// api-contract §3.7 — NotificationResponse + type별 payload 계약
export interface SlotUser {
  id: number
  nickname: string
}

export type Slot =
  | { type: 'TIME'; date: string; startTime: string; endTime: string }
  | { type: 'DATE'; startDate: string; endDate: string }

export type NotificationType =
  | 'FRIEND_REQUEST'
  | 'FRIEND_ACCEPTED'
  | 'PROPOSAL_RECEIVED'
  | 'PROPOSAL_ALL_ACCEPTED'
  | 'PROPOSAL_CONFIRMED'
  | 'PROPOSAL_DECLINED'

export interface NotificationItem {
  id: number
  type: NotificationType
  payload: Record<string, unknown>
  readAt: string | null
  createdAt: string
}

export interface NotificationPage {
  content: NotificationItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  unreadCount: number
}
