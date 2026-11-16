import { describe, expect, it } from 'vitest'
import { formatKoDate, nextWeekdayISO } from './date'

describe('nextWeekdayISO', () => {
  it('오늘이 금요일이면 다음 금요일을 반환한다', () => {
    // 2026-11-20은 금요일 — nextWeekdayISO(5)는 2026-11-27
    const d = nextWeekdayISO(5, new Date(2026, 10, 20))
    expect(d.getDay()).toBe(5)
    expect(d.getDate()).toBe(27)
  })
  it('오늘이 수요일이면 다가오는 금요일을 반환한다', () => {
    const d = nextWeekdayISO(5, new Date(2026, 10, 18)) // 수요일
    expect(d.getDay()).toBe(5)
    expect(d.getDate()).toBe(20)
  })
})

describe('formatKoDate', () => {
  it('"M월 d일 (요일)" 형식으로 포맷한다', () => {
    expect(formatKoDate(new Date(2026, 10, 20))).toBe('11월 20일 (금)')
  })
})
