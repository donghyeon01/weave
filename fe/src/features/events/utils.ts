import type { EventItem } from './types'

// 달력 날짜 계산은 전부 로컬 타임존 기준 — 서버는 TZ 없는 LocalDateTime 문자열을 준다.

export function toYmd(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function parseYmd(ymd: string): Date {
  const [y, m, d] = ymd.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(d: Date, n: number): Date {
  const next = new Date(d)
  next.setDate(next.getDate() + n)
  return next
}

// 월 그리드는 일요일 시작(일~토) — 그리드 첫 셀은 그 달 1일이 속한 주의 일요일.
export function monthGridRange(year: number, month: number): { start: Date; end: Date } {
  const first = new Date(year, month, 1)
  const start = addDays(first, -first.getDay())
  const last = new Date(year, month + 1, 0)
  const end = addDays(last, 6 - last.getDay())
  return { start, end }
}

export function monthGridDays(year: number, month: number): Date[] {
  const { start, end } = monthGridRange(year, month)
  const days: Date[] = []
  for (let d = start; d <= end; d = addDays(d, 1)) days.push(d)
  return days
}

export function addMonths(year: number, month: number, n: number): { year: number; month: number } {
  const d = new Date(year, month + n, 1)
  return { year: d.getFullYear(), month: d.getMonth() }
}

// 서버 시간 문자열 "yyyy-MM-dd'T'HH:mm:ss"의 날짜 부분만 꺼낸다.
export function datePart(localDateTime: string): string {
  return localDateTime.slice(0, 10)
}

export function timePart(localDateTime: string): string {
  return localDateTime.slice(11, 16)
}

// 이벤트가 특정 날짜를 덮는지 — 시작일~종료일(포함) 사이면 해당.
export function eventCoversDate(event: EventItem, ymd: string): boolean {
  return datePart(event.startTime) <= ymd && ymd <= datePart(event.endTime)
}

export function sortByStart(events: EventItem[]): EventItem[] {
  return [...events].sort((a, b) => a.startTime.localeCompare(b.startTime))
}

export function formatEventTime(event: EventItem): string {
  if (event.allDay) return '종일'
  return `${timePart(event.startTime)}~${timePart(event.endTime)}`
}

// task.dueDate 기준 오늘까지 남은 일수 — 0이면 D-Day다.
export function ddayOffset(dueDate: string, today: string): number {
  const diff = parseYmd(dueDate).getTime() - parseYmd(today).getTime()
  return Math.round(diff / 86_400_000)
}

export function formatDday(offset: number): string {
  if (offset === 0) return 'D-Day'
  return offset > 0 ? `D-${offset}` : `D+${-offset}`
}

// datetime-local 입력값 "yyyy-MM-ddTHH:mm" → 서버 형식 "yyyy-MM-dd'T'HH:mm:ss"
export function toServerDateTime(inputValue: string): string {
  return `${inputValue}:00`
}
