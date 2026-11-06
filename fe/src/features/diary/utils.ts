// feature 간 import 금지라 events/utils를 공유할 수 없어 필요한 최소 날짜 헬퍼만 둔다.
export function toYmd(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

// "2026년 5월 12일 (화)"
export function formatDateHeading(ymd: string): string {
  const [y, m, d] = ymd.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${y}년 ${m}월 ${d}일 (${weekday})`
}
