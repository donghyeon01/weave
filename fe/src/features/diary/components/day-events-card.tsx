import { useDayEvents } from '../api/use-day-events'

// 우측 레일 "연결된 일정" — 시안 4:366 Card 1: 당일 events를 읽기 전용 카드로 보여준다.
export function DayEventsCard({ date }: { date: string }) {
  const eventsQuery = useDayEvents(date)
  const events = eventsQuery.data ?? []

  if (eventsQuery.isPending || events.length === 0) return null

  return (
    <section className="rounded-2xl border border-border bg-surface p-5 shadow-card-sm">
      <h3 className="font-display text-lg">연결된 일정</h3>
      <ul className="mt-3 flex flex-col gap-2">
        {events.map((e) => (
          <li
            key={e.id}
            className="flex items-baseline gap-2 rounded-lg bg-surface-muted px-3 py-2 text-sm"
          >
            <span className="shrink-0 text-xs font-semibold text-text-secondary">
              {e.allDay ? '종일' : `${e.startTime.slice(11, 16)} - ${e.endTime.slice(11, 16)}`}
            </span>
            <span className="truncate font-semibold text-text">{e.title}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
