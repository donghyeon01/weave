const WEEKDAYS_KO = ['일', '월', '화', '수', '목', '금', '토'] as const

export function nextWeekdayISO(day: number, from: Date = new Date()): Date {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  const diff = (day - d.getDay() + 7) % 7 || 7
  d.setDate(d.getDate() + diff)
  return d
}

export function formatKoDate(d: Date): string {
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS_KO[d.getDay()]})`
}
