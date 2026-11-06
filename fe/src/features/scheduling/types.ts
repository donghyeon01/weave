// api-contract §3.8 — Slot·추천·제안 DTO
export type Slot =
  | { type: 'TIME'; date: string; startTime: string; endTime: string }
  | { type: 'DATE'; startDate: string; endDate: string }

export interface RecommendRequest {
  mode: 'TIME' | 'DATE'
  rangeStart: string
  rangeEnd: string
  participantIds?: number[]
  windowStart?: string
  windowEnd?: string
  slotMinutes?: number
  durationDays?: number
}

export interface Candidate {
  rank: number
  percent: number
  availableCount: number
  totalCount: number
  slot: Slot
}

export interface ProposalUser {
  id: number
  nickname: string
}

export interface ProposalParticipant {
  user: ProposalUser
  response: 'PENDING' | 'ACCEPTED' | 'DECLINED'
  respondedAt: string | null
}

export type ProposalStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED'

export interface ProposalResponse {
  id: number
  title: string
  status: ProposalStatus
  slot: Slot
  organizer: ProposalUser
  participants: ProposalParticipant[]
  createdAt: string
}

export interface ProposalSummary {
  id: number
  title: string
  status: ProposalStatus
  slot: Slot
  organizer: ProposalUser
  acceptCount: number
  totalCount: number
  createdAt: string
}

export interface ProposalPage {
  content: ProposalSummary[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function formatSlot(slot: Slot): string {
  if (slot.type === 'TIME') return `${slot.date} ${slot.startTime}~${slot.endTime}`
  return `${slot.startDate} ~ ${slot.endDate}`
}
