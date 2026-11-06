import { useState } from 'react'
import { DiaryEditor } from './diary-editor'
import { DiaryMonthNav } from './diary-month-nav'
import { toYmd } from '../utils'

export function DiaryPage() {
  const today = toYmd(new Date())
  const [selectedDate, setSelectedDate] = useState(today)
  const [yearMonth, setYearMonth] = useState(today.slice(0, 7))

  // 다른 달의 기록을 목록에서 고르면 네비게이터 표시 월도 그 달로 맞춘다.
  const selectDate = (date: string) => {
    setSelectedDate(date)
    setYearMonth(date.slice(0, 7))
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
      <DiaryMonthNav
        yearMonth={yearMonth}
        selectedDate={selectedDate}
        onMonthChange={setYearMonth}
        onSelectDate={selectDate}
        onToday={() => selectDate(toYmd(new Date()))}
      />
      <section className="rounded-2xl border-2 border-border-strong bg-surface p-6 shadow-brutal-sm">
        <DiaryEditor date={selectedDate} />
      </section>
    </div>
  )
}
