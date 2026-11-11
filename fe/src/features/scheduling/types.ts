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
  /** 분리된 다중 탐색 창 — 비어있지 않으면 windowStart/End보다 우선한다 (계약 §3.8) */
  windows?: { startTime: string; endTime: string }[]
  /** 탐색 간격(분) — 미지정 시 be 기본 30 */
  stepMinutes?: number
  slotMinutes?: number
  durationDays?: number
}

// 추천 슬롯의 판정 근거 5분류 — 합계는 항상 totalCount와 같다 (계약 §3.8).
export interface CandidateBreakdown {
  /** 완료 선언(직접 입력)이 슬롯 전체를 덮은 인원 */
  direct: number
  /** 직접 선언+캘린더 추론 혼합 또는 순수 추론으로 가능 판정된 인원 */
  calendarFree: number
  /** SOFT 일정이 겹쳐 조정이 필요한 인원 */
  conditional: number
  /** 가용 시간 미입력 등 근거 부족 — 가능으로 집계하지 않는다 */
  unknown: number
  /** HARD 겹침 또는 완료 범위 내 미선택 구간 존재 */
  unavailable: number
}

export interface Candidate {
  rank: number
  /** 근거상 가능 판정 인원 비율 — floor(100×availableCount/totalCount). 참석 확률이 아니다 */
  percent: number
  /** direct + calendarFree */
  availableCount: number
  totalCount: number
  breakdown: CandidateBreakdown
  slot: Slot
}

export interface CandidatesResponse {
  candidates: Candidate[]
  /** 계산 시점 참여자 스냅샷 — 주최자 제외 */
  participantIds: number[]
  /** ISO instant */
  computedAt: string
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

export type ProposalStatus =
  | 'PENDING'
  | 'ALL_ACCEPTED'
  | 'CONFIRMED'
  | 'DECLINED'
  | 'CANCELLED'

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
