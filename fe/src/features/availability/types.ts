// api-contract §3.12 — 가용 시간 범위·블록·선호 DTO
export type AvailabilityMode = 'TIME' | 'DATE'
export type AvailabilityScopeStatus = 'INPUT' | 'COMPLETED' | 'CANCELLED'
export type AvailabilityBlockKind = 'TIME' | 'DATE'

export interface AvailabilityBlock {
  kind: AvailabilityBlockKind
  /** kind=TIME 전용 — 'yyyy-MM-ddTHH:mm:ss', endAt은 배타 상한 */
  startAt?: string
  endAt?: string
  /** kind=DATE 전용 — 'yyyy-MM-dd' */
  blockDate?: string
}

export interface AvailabilityScopeSummary {
  id: number
  mode: AvailabilityMode
  rangeStart: string
  rangeEnd: string
  status: AvailabilityScopeStatus
  revision: number
  blockCount: number
  createdAt: string
  updatedAt: string
}

export interface AvailabilityScopeDetail extends Omit<AvailabilityScopeSummary, 'blockCount'> {
  blocks: AvailabilityBlock[]
}

export interface AvailabilityPreferences {
  useCalendarInference: boolean
}

export interface CreateScopeRequest {
  mode: AvailabilityMode
  rangeStart: string
  rangeEnd: string
}

export interface PutBlocksRequest {
  blocks: AvailabilityBlock[]
  /** "COMPLETED"만 허용 — 생략 시 현재 상태 유지 (INPUT 중간 저장) */
  status?: 'COMPLETED'
  /** 낙관 동시성 제어 — 서버 revision과 다르면 409 AVAILABILITY-REVISION-CONFLICT */
  expectedRevision: number
}
