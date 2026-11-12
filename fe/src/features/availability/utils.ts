import type { AvailabilityBlock } from './types'

// 날짜는 'yyyy-MM-dd' 문자열, 시각은 'yyyy-MM-ddTHH:mm:ss' naive local (KST) —
// toISOString은 UTC 변환으로 날짜가 어긋나므로 문자열 파싱만 사용한다 (scheduling-page 관례).

const DOW = ['일', '월', '화', '수', '목', '금', '토'] as const

export function parseYmd(s: string): { y: number; m: number; d: number } {
  const [y, m, d] = s.split('-').map(Number)
  return { y, m, d }
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function formatYmd(y: number, m: number, d: number): string {
  return `${y}-${pad(m)}-${pad(d)}`
}

/** epoch 일수 — 날짜 간 뺄셈·요일 계산용 (UTC 기준이라 TZ 무관) */
function epochDay(y: number, m: number, d: number): number {
  return Math.floor(Date.UTC(y, m - 1, d) / 86_400_000)
}

export function addDays(ymd: string, days: number): string {
  const { y, m, d } = parseYmd(ymd)
  const dt = new Date(Date.UTC(y, m - 1, d + days))
  return formatYmd(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate())
}

export function diffDays(a: string, b: string): number {
  const pa = parseYmd(a)
  const pb = parseYmd(b)
  return epochDay(pb.y, pb.m, pb.d) - epochDay(pa.y, pa.m, pa.d)
}

/** rangeStart~rangeEnd 포함 날짜 목록 */
export function dateList(rangeStart: string, rangeEnd: string): string[] {
  const n = diffDays(rangeStart, rangeEnd)
  if (n < 0) return []
  return Array.from({ length: n + 1 }, (_, i) => addDays(rangeStart, i))
}

export function dayOfWeek(ymd: string): string {
  const { y, m, d } = parseYmd(ymd)
  // epochDay 0 = 1970-01-01 목요일
  return DOW[(epochDay(y, m, d) + 4) % 7]
}

/** 월요일 시작 주의 첫째 요일 인덱스 (0=월..6=일) — DATE 그리드 앞 공백용 */
export function mondayIndex(ymd: string): number {
  const { y, m, d } = parseYmd(ymd)
  // epochDay 0 = 1970-01-01 목요일 → 월 기준 인덱스는 (epochDay+3)%7
  return (epochDay(y, m, d) + 3) % 7
}

/** "11월 10일 (월) – 11월 16일 (일) · 7일" — 같은 달이면 끝쪽 월 생략 */
export function periodLabel(rangeStart: string, rangeEnd: string): string {
  const a = parseYmd(rangeStart)
  const b = parseYmd(rangeEnd)
  const start = `${a.m}월 ${a.d}일 (${dayOfWeek(rangeStart)})`
  const end =
    a.m === b.m && a.y === b.y
      ? `${b.d}일 (${dayOfWeek(rangeEnd)})`
      : `${b.m}월 ${b.d}일 (${dayOfWeek(rangeEnd)})`
  return `${start} – ${end} · ${diffDays(rangeStart, rangeEnd) + 1}일`
}

export function todayYmd(): string {
  const now = new Date()
  return formatYmd(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

// ── TIME 그리드 셀 ──────────────────────────────────────────────
// 셀 키 = "yyyy-MM-dd@분" (자정부터의 분). 데이터 원자 단위는 15분 고정 —
// 표시 셀 크기(15/30/60분 토글)는 순수 시각 그룹화라 해상도 전환 시 데이터 손실이 없다.

export type CellKey = string

/** 데이터 원자 — 표시 토글과 무관하게 항상 15분 격자 */
export const ATOM_MIN = 15
export const ATOMS_PER_DAY = (24 * 60) / ATOM_MIN

export const CELL_STEPS = [15, 30, 60] as const
export type CellStep = (typeof CELL_STEPS)[number]
export const DEFAULT_CELL_STEP: CellStep = 30

// 셀 키 구분자는 '@' — '#'는 check:tokens가 hex 색으로 오인한다
export function cellKey(date: string, minuteOfDay: number): CellKey {
  return `${date}@${minuteOfDay}`
}

export function parseCellKey(key: CellKey): { date: string; minute: number } {
  const [date, minute] = key.split('@')
  return { date, minute: Number(minute) }
}

/** 표시 슬롯 — stepMin 간격, 하루 1440/stepMin 개 */
export function visualSlots(stepMin: number): { index: number; minute: number; label: string }[] {
  const out: { index: number; minute: number; label: string }[] = []
  for (let i = 0; i < 1440 / stepMin; i++) {
    const m = i * stepMin
    out.push({ index: i, minute: m, label: `${pad(Math.floor(m / 60))}:${pad(m % 60)}` })
  }
  return out
}

/** 표시 셀 하나가 덮는 원자 키 목록 */
export function slotAtomKeys(date: string, slotMinute: number, stepMin: number): CellKey[] {
  const keys: CellKey[] = []
  for (let m = slotMinute; m < slotMinute + stepMin; m += ATOM_MIN) {
    keys.push(cellKey(date, m))
  }
  return keys
}

function absMinutes(ymd: string, minuteOfDay: number): number {
  const { y, m, d } = parseYmd(ymd)
  return epochDay(y, m, d) * 1440 + minuteOfDay
}

function ymdOfAbs(abs: number): string {
  const day = Math.floor(abs / 1440)
  const dt = new Date(day * 86_400_000)
  return formatYmd(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate())
}

/** datetime 문자열 → 절대 분 (naive local — 날짜부만 epoch으로 환산) */
function parseDateTimeMin(s: string): number {
  const date = s.slice(0, 10)
  const time = s.slice(11, 16)
  const [hh, mm] = time.split(':').map(Number)
  return absMinutes(date, hh * 60 + mm)
}

/**
 * 저장된 TIME 블록 → 페인트된 원자 셀 집합 (항상 15분 격자).
 * 셀 [m, m+15)과 블록 [startAt, endAt)의 겹침이 있으면 칠한 것으로 본다 —
 * 분 단위로 정렬되지 않은 기존 블록은 15분 격자로 정규화되어 표시·재저장된다.
 */
export function blocksToTimeCells(blocks: AvailabilityBlock[]): Set<CellKey> {
  const cells = new Set<CellKey>()
  for (const b of blocks) {
    if (b.kind !== 'TIME' || !b.startAt || !b.endAt) continue
    const start = parseDateTimeMin(b.startAt)
    const end = parseDateTimeMin(b.endAt)
    const firstDay = Math.floor(start / 1440)
    const lastDay = Math.floor((end - 1) / 1440)
    for (let day = firstDay; day <= lastDay; day++) {
      const dayStart = day * 1440
      const lo = Math.max(start, dayStart) - dayStart
      const hi = Math.min(end, dayStart + 1440) - dayStart
      if (lo >= hi) continue
      for (let m = Math.floor(lo / ATOM_MIN) * ATOM_MIN; m < hi; m += ATOM_MIN) {
        if (m + ATOM_MIN > lo) cells.add(cellKey(ymdOfAbs(dayStart), m))
      }
    }
  }
  return cells
}

/**
 * 페인트된 원자 셀 집합 → 저장용 TIME 블록 (날짜별 연속 구간 병합, endAt 배타).
 * 24:00까지 칠해진 구간은 익일 00:00으로 표기한다.
 */
export function timeCellsToBlocks(cells: Set<CellKey>): AvailabilityBlock[] {
  const byDate = new Map<string, number[]>()
  for (const key of cells) {
    const { date, minute } = parseCellKey(key)
    const arr = byDate.get(date) ?? []
    arr.push(minute)
    byDate.set(date, arr)
  }
  const blocks: AvailabilityBlock[] = []
  for (const [date, minutes] of [...byDate.entries()].sort()) {
    minutes.sort((a, b) => a - b)
    let runStart = minutes[0]
    let runEnd = minutes[0] + ATOM_MIN
    const flush = () => {
      const startAbs = absMinutes(date, runStart)
      const endAbs = absMinutes(date, runEnd)
      blocks.push({
        kind: 'TIME',
        startAt: `${ymdOfAbs(startAbs)}T${pad(Math.floor((startAbs % 1440) / 60))}:${pad(startAbs % 60)}:00`,
        endAt: `${ymdOfAbs(endAbs)}T${pad(Math.floor((endAbs % 1440) / 60))}:${pad(endAbs % 60)}:00`,
      })
    }
    for (let i = 1; i < minutes.length; i++) {
      if (minutes[i] === runEnd) {
        runEnd = minutes[i] + ATOM_MIN
      } else {
        flush()
        runStart = minutes[i]
        runEnd = minutes[i] + ATOM_MIN
      }
    }
    flush()
  }
  return blocks
}

/** DATE 모드 — 선택된 날짜 집합 → 블록 (날짜당 1블록) */
export function datesToBlocks(dates: Set<string>): AvailabilityBlock[] {
  return [...dates].sort().map((d) => ({ kind: 'DATE' as const, blockDate: d }))
}

/** 저장된 DATE 블록 → 선택된 날짜 집합 */
export function blocksToDates(blocks: AvailabilityBlock[]): Set<string> {
  const out = new Set<string>()
  for (const b of blocks) {
    if (b.kind === 'DATE' && b.blockDate) out.add(b.blockDate)
  }
  return out
}
