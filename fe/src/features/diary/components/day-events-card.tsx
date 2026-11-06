import { useDayEvents } from '../api/use-day-events'

// 에디터 하단 "이 날의 일정" — 당일 events를 읽기 전용 카드로 보여준다.
export function DayEventsCard({ date }: { date: string }) {
  const eventsQuery = useDayEvents(date)
  const events = eventsQuery.data ?? []

  if (eventsQuery.isPending || events.length === 0) return null

  return (
    <section className="rounded-2xl border-2 border-border bg-surface-muted p-4">
      <h3 className="text-sm font-bold text-text-secondary">이 날의 일정</h3>
      <ul className="mt-2 flex flex-col gap-1.5">
        {events.map((e) => (
          <li key={e.id} className="flex items-baseline gap-2 text-sm">
            <span className="shrink-0 text-xs font-semibold text-text-muted">
              {e.allDay ? '종일' : e.startTime.slice(11, 16)}
            </span>
            <span className="truncate font-semibold text-text">{e.title}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
