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
  /** 요일 반복 패턴 — TIME 전용·선택 (계약 §3.8 V2 scheduling 확장) */
  recurrence?: RecurrenceSpec
}

// api-contract §3.8 — WEEKLY만 지원. count는 첫 회차 포함 총 발생 횟수(2~12).
export interface RecurrenceSpec {
  freq: 'WEEKLY'
  /** 1~4주 간격 */
  interval: number
  /** ISO 요일 집합 (월=1~일=7), 비어있으면 안 됨 */
  byDay: number[]
  count: number
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
  /** 0~100 — 선호 등록 참여자의 후보 슬롯 적합도 평균. null이면 미표시(0점은 표시) */
  preferenceScore?: number | null
  /** 점수에 참여한 고유 사용자 수 */
  preferenceRespondentCount?: number | null
  /** 전개된 총 회차 수 — 단일이면 null */
  occurrenceCount?: number | null
  /** 회차별 percent 최솟값 — 단일이면 null */
  minOccurrencePercent?: number | null
  /** 회차별 상세 — 단일이면 null */
  occurrences?: CandidateOccurrence[] | null
  slot: Slot
}

// api-contract §3.8 — recurrence 요청 시에만 채워진다. 단일 후보는 전부 null/생략.
export interface CandidateOccurrence {
  date: string
  percent: number
  availableCount: number
  breakdown: CandidateBreakdown
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

// api-contract §3.7 — ProposalResponse의 recurrence/occurrenceCount/occurrences는
// V2 scheduling 확장 additive. slot은 첫 회차 스냅샷 — 기존 소비자 호환.
export interface ProposalOccurrence {
  date: string
  startTime: string
  endTime: string
}

export interface ProposalResponse {
  id: number
  title: string
  status: ProposalStatus
  slot: Slot
  /** 정례 제안이면 반복 패턴, 단일이면 null */
  recurrence: RecurrenceSpec | null
  /** 시리즈 총 회차 수 — 단일이면 1 */
  occurrenceCount: number
  /** 정례 제안의 회차 목록 — TIME만, 단일 제안은 생략 */
  occurrences?: ProposalOccurrence[]
  organizer: ProposalUser
  participants: ProposalParticipant[]
  createdAt: string
}

export interface ProposalSummary {
  id: number
  title: string
  status: ProposalStatus
  slot: Slot
  recurrence: RecurrenceSpec | null
  /** 시리즈 총 회차 수 — 단일이면 1 */
  occurrenceCount: number
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

// ISO 요일(월=1~일=7) → 한글 라벨 — RecurrenceSpec.byDay 표기용
const ISO_WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'] as const

// "매주 화·목" / "2주마다 화·목" — RecurrenceSpec을 사람이 읽는 반복 주기로 바꾼다
export function formatRecurrence(spec: RecurrenceSpec): string {
  const days = [...spec.byDay]
    .sort((a, b) => a - b)
    .map((iso) => ISO_WEEKDAY_LABELS[iso - 1] ?? '')
    .join('·')
  const every = spec.interval === 1 ? '매주' : `${spec.interval}주마다`
  return `${every} ${days}`
}

const SUN_FIRST_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

// "5/22 (목)" — 회차·기간 표기의 짧은 날짜
export function formatShortDate(ymd: string): string {
  const d = new Date(`${ymd}T00:00:00`)
  return `${d.getMonth() + 1}/${d.getDate()} (${SUN_FIRST_WEEKDAYS[d.getDay()]})`
}
