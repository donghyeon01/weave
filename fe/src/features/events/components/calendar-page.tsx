import { useState } from 'react'
import type { EventItem } from '../types'
import { Button } from '../../../components/ui/button'
import { Toast } from '../../../components/ui/toast'
import { useEventsRange } from '../api/use-events-range'
import { useTaskDdays } from '../api/use-task-ddays'
import { CalendarGrid } from './calendar-grid'
import { LEGEND_ITEMS } from '../legend'
import { DaySidebar } from './day-sidebar'
import { EventDetailModal } from './event-detail-modal'
import { EventFormModal } from './event-form-modal'
import { addMonths, eventCoversDate, monthGridRange, toYmd } from '../utils'

type FormTarget = { mode: 'create'; date: string } | { mode: 'edit'; event: EventItem }

export function CalendarPage() {
  const today = toYmd(new Date())
  const [view, setView] = useState(() => {
    const now = new Date()
    return { year: now.getFullYear(), month: now.getMonth() }
  })
  const [selectedDate, setSelectedDate] = useState(today)
  const [detailEvent, setDetailEvent] = useState<EventItem | null>(null)
  const [formTarget, setFormTarget] = useState<FormTarget | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  // 그리드에 보이는 이전·다음 달 날짜까지 커버하도록 셀 범위 전체를 조회한다.
  const { start, end } = monthGridRange(view.year, view.month)
  const eventsQuery = useEventsRange(toYmd(start), toYmd(end))
  const ddaysQuery = useTaskDdays()

  const events = eventsQuery.data ?? []
  const ddayTasks = ddaysQuery.data ?? []
  const selectedDayEvents = events.filter((e) => eventCoversDate(e, selectedDate))

  const moveMonth = (n: number) => setView((v) => addMonths(v.year, v.month, n))
  const goToday = () => {
    const now = new Date()
    setView({ year: now.getFullYear(), month: now.getMonth() })
    setSelectedDate(toYmd(now))
  }

  const openEdit = (event: EventItem) => {
    setDetailEvent(null)
    setFormTarget({ mode: 'edit', event })
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" onClick={() => moveMonth(-1)} aria-label="이전 달">
            ◀
          </Button>
          <h1 className="font-display text-2xl">
            {view.year}년 {view.month + 1}월
          </h1>
          <Button variant="ghost" size="sm" onClick={() => moveMonth(1)} aria-label="다음 달">
            ▶
          </Button>
          <Button variant="primary" size="sm" onClick={goToday}>
            오늘
          </Button>
        </div>
        <ul className="flex flex-wrap items-center gap-3" aria-label="일정 범례">
          {LEGEND_ITEMS.map((item) => (
            <li key={item.key} className="flex items-center gap-1.5 text-xs font-semibold text-text-secondary">
              <span className={`inline-block h-3 w-3 rounded ${item.swatch}`} />
              {item.label}
            </li>
          ))}
        </ul>
      </div>

      <div className="mt-5 grid gap-6 lg:grid-cols-[1fr_320px]">
        <div>
          {eventsQuery.isPending && (
            <p className="mb-2 text-sm text-text-muted">일정을 불러오는 중…</p>
          )}
          {eventsQuery.isError && (
            <p role="alert" className="mb-2 text-sm font-semibold text-danger-text">
              일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}
          <CalendarGrid
            year={view.year}
            month={view.month}
            selectedDate={selectedDate}
            today={today}
            events={events}
            ddayTasks={ddayTasks}
            onSelectDate={setSelectedDate}
            onEventClick={setDetailEvent}
          />
        </div>
        <DaySidebar
          date={selectedDate}
          events={selectedDayEvents}
          onEventClick={setDetailEvent}
          onAddClick={() => setFormTarget({ mode: 'create', date: selectedDate })}
        />
      </div>

      {detailEvent !== null && (
        <EventDetailModal
          event={detailEvent}
          onClose={() => setDetailEvent(null)}
          onEdit={() => openEdit(detailEvent)}
          onDeleted={() => {
            setDetailEvent(null)
            setToast('일정을 삭제했어요.')
          }}
        />
      )}
      {formTarget !== null && (
        <EventFormModal
          mode={formTarget.mode}
          defaultDate={formTarget.mode === 'create' ? formTarget.date : today}
          event={formTarget.mode === 'edit' ? formTarget.event : undefined}
          onClose={() => setFormTarget(null)}
          onSaved={(message) => {
            setFormTarget(null)
            setToast(message)
          }}
        />
      )}
      {toast !== null && (
        <Toast variant="success" message={toast} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
