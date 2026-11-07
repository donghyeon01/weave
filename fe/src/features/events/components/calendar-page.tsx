import { useMemo, useState } from 'react'
import type { EventItem } from '../types'
import { Button } from '../../../components/ui/button'
import { Toast } from '../../../components/ui/toast'
import { cx } from '../../../utils/cx'
import { useEventsRange } from '../api/use-events-range'
import { useTaskDdays } from '../api/use-task-ddays'
import { CalendarGrid } from './calendar-grid'
import { DayView } from './day-view'
import { WeekStrip } from './week-strip'
import { LEGEND_ITEMS } from '../legend'
import { DaySidebar } from './day-sidebar'
import { EventDetailModal } from './event-detail-modal'
import { EventFormModal } from './event-form-modal'
import { addDays, addMonths, eventCoversDate, monthGridRange, parseYmd, toYmd } from '../utils'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'

type FormTarget = { mode: 'create'; date: string } | { mode: 'edit'; event: EventItem }
type ViewMode = 'month' | 'week' | 'day'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

function weekStart(d: Date): Date {
  return addDays(d, -d.getDay())
}

// 시안 헤더 표기 — 월 "2025년 5월" / 주 "2025년 5월 11일 – 17일" / 일 "2025년 5월 16일 (금)"
function viewTitle(mode: ViewMode, view: { year: number; month: number }, anchor: Date): string {
  if (mode === 'month') return `${view.year}년 ${view.month + 1}월`
  if (mode === 'day')
    return `${anchor.getFullYear()}년 ${anchor.getMonth() + 1}월 ${anchor.getDate()}일 (${WEEKDAYS[anchor.getDay()]})`
  const end = addDays(anchor, 6)
  const sameMonth = anchor.getMonth() === end.getMonth()
  return `${anchor.getFullYear()}년 ${anchor.getMonth() + 1}월 ${anchor.getDate()}일 – ${
    sameMonth ? '' : `${end.getMonth() + 1}월 `
  }${end.getDate()}일`
}

// Figma 4:1573(월)·147:438(주)·147:816(일) — 뷰 토글 + 셀 더블클릭 빠른 추가 + 우측 일정 패널.
export function CalendarPage() {
  const today = toYmd(new Date())
  const [mode, setMode] = useState<ViewMode>('month')
  const [view, setView] = useState(() => {
    const now = new Date()
    return { year: now.getFullYear(), month: now.getMonth() }
  })
  const [selectedDate, setSelectedDate] = useState(today)
  const [detailEvent, setDetailEvent] = useState<EventItem | null>(null)
  const [formTarget, setFormTarget] = useState<FormTarget | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const anchor = parseYmd(selectedDate)

  // 보이는 기간 전체를 조회한다 (월 그리드의 인접 달 날짜까지 포함).
  const { start, end } = useMemo(() => {
    if (mode === 'month') return monthGridRange(view.year, view.month)
    if (mode === 'week') {
      const ws = weekStart(anchor)
      return { start: ws, end: addDays(ws, 6) }
    }
    return { start: anchor, end: anchor }
  }, [mode, view, anchor])
  const eventsQuery = useEventsRange(toYmd(start), toYmd(end))
  const ddaysQuery = useTaskDdays()

  const events = eventsQuery.data ?? []
  const ddayTasks = ddaysQuery.data ?? []
  const selectedDayEvents = events.filter((e) => eventCoversDate(e, selectedDate))

  const move = (n: number) => {
    if (mode === 'month') {
      setView((v) => addMonths(v.year, v.month, n))
      return
    }
    const next = addDays(anchor, mode === 'week' ? n * 7 : n)
    setSelectedDate(toYmd(next))
    setView({ year: next.getFullYear(), month: next.getMonth() })
  }

  const goToday = () => {
    const now = new Date()
    setView({ year: now.getFullYear(), month: now.getMonth() })
    setSelectedDate(toYmd(now))
  }

  const selectDate = (ymd: string) => {
    setSelectedDate(ymd)
    const d = parseYmd(ymd)
    setView({ year: d.getFullYear(), month: d.getMonth() })
  }

  const openEdit = (event: EventItem) => {
    setDetailEvent(null)
    setFormTarget({ mode: 'edit', event })
  }

  const weekAnchor = weekStart(anchor)
  const title = viewTitle(mode, view, mode === 'week' ? weekAnchor : anchor)

  return (
    <div className="mx-auto flex w-full max-w-[1400px] gap-6 p-8">
      {/* 캘린더 본체 — 시안 Calendar Main Section */}
      <section className="min-w-0 flex-1 rounded-2xl border border-border bg-surface p-6 shadow-[0_1px_1px_rgba(0,0,0,0.05)]">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-[32px] leading-10 tracking-tight">{title}</h1>
            <div className="flex items-center gap-1 rounded-xl border border-border bg-surface-muted p-1">
              <button
                type="button"
                onClick={() => move(-1)}
                aria-label="이전"
                className="flex size-8 items-center justify-center rounded-lg text-text-secondary hover:bg-surface"
              >
                ‹
              </button>
              <button
                type="button"
                onClick={() => move(1)}
                aria-label="다음"
                className="flex size-8 items-center justify-center rounded-lg text-text-secondary hover:bg-surface"
              >
                ›
              </button>
            </div>
            <Button variant="ghost" size="sm" onClick={goToday}>
              오늘
            </Button>
          </div>
          <div className="flex items-center gap-3">
            <div
              role="tablist"
              aria-label="캘린더 뷰"
              className="flex items-center rounded-xl border border-border bg-surface-muted p-1"
            >
              {(
                [
                  { key: 'month', label: '월' },
                  { key: 'week', label: '주' },
                  { key: 'day', label: '일' },
                ] as const
              ).map((t) => (
                <button
                  key={t.key}
                  type="button"
                  role="tab"
                  aria-selected={mode === t.key}
                  onClick={() => setMode(t.key)}
                  className={cx(
                    'rounded-lg px-3 py-1.5 text-sm font-bold transition duration-150',
                    mode === t.key
                      ? 'border border-border bg-surface text-accent shadow-sm'
                      : 'text-text-secondary hover:text-text',
                  )}
                >
                  {t.label}
                </button>
              ))}
            </div>
            <Button
              variant="primary"
              size="sm"
              onClick={() => setFormTarget({ mode: 'create', date: selectedDate })}
            >
              + 새 일정
            </Button>
          </div>
        </div>

        <div className="mt-5">
          {eventsQuery.isPending && <SkeletonRow rows={4} />}
          <QueryErrorToast
            show={eventsQuery.isError}
            message="일정을 불러오지 못했어요. 다시 시도해 주세요."
          />
          {mode === 'month' && (
            <CalendarGrid
              year={view.year}
              month={view.month}
              selectedDate={selectedDate}
              today={today}
              events={events}
              ddayTasks={ddayTasks}
              onSelectDate={selectDate}
              onEventClick={setDetailEvent}
              onDoubleClickDate={(ymd) => setFormTarget({ mode: 'create', date: ymd })}
            />
          )}
          {mode === 'week' && (
            <WeekStrip
              weekStart={weekAnchor}
              selectedDate={selectedDate}
              today={today}
              events={events}
              ddayTasks={ddayTasks}
              onSelectDate={selectDate}
              onEventClick={setDetailEvent}
              onDoubleClickDate={(ymd) => setFormTarget({ mode: 'create', date: ymd })}
            />
          )}
          {mode === 'day' && (
            <DayView
              date={selectedDate}
              events={selectedDayEvents}
              onEventClick={setDetailEvent}
              onDoubleClickTime={() => setFormTarget({ mode: 'create', date: selectedDate })}
            />
          )}
        </div>

        <ul className="mt-4 flex flex-wrap items-center gap-4" aria-label="일정 범례">
          {LEGEND_ITEMS.map((item) => (
            <li
              key={item.key}
              className="flex items-center gap-1.5 text-xs font-semibold text-text-secondary"
            >
              <span className={`inline-block h-3 w-3 rounded ${item.swatch}`} />
              {item.label}
            </li>
          ))}
        </ul>
        <p className="mt-1.5 text-xs font-medium text-text-muted">
          팁: 빈 날짜 셀을 더블클릭하면 즉시 간편 일정이 추가됩니다.
        </p>
      </section>

      {/* 우측 일정 패널 — 시안 "5월 16일 (금) 일정" */}
      <DaySidebar
        date={selectedDate}
        events={selectedDayEvents}
        onEventClick={setDetailEvent}
        onAddClick={() => setFormTarget({ mode: 'create', date: selectedDate })}
      />

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
