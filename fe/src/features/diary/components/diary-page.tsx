import { useState } from 'react'
import { DiaryEditor } from './diary-editor'
import { DayEventsCard } from './day-events-card'
import { RecentDiariesCard } from './recent-diaries-card'
import { toYmd } from '../utils'

// Figma 4:366 — 2/3 작성 카드 + 1/3 우측 레일(연결된 일정·지난 일기). 월 네비는 시안에 없어 제거.
export function DiaryPage() {
  const today = toYmd(new Date())
  const [selectedDate, setSelectedDate] = useState(today)

  return (
    <div className="mx-auto w-full max-w-7xl p-8">
      <div className="grid gap-8 lg:grid-cols-[2fr_1fr]">
        <DiaryEditor date={selectedDate} />
        <aside className="flex flex-col gap-6">
          <DayEventsCard date={selectedDate} />
          <RecentDiariesCard
            selectedDate={selectedDate}
            onSelectDate={setSelectedDate}
          />
        </aside>
      </div>
    </div>
  )
}
