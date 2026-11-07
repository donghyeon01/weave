import type { EventItem, TaskItem } from '../types'
import { cx } from '../../../utils/cx'
import { CHIP_STYLES } from '../legend'
import { addDays, ddayOffset, eventCoversDate, formatDday, sortByStart, toYmd } from '../utils'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const
const MAX_CELL_ITEMS = 4

interface WeekStripProps {
  /** 스트립 첫날(일요일) */
  weekStart: Date
  selectedDate: string
  today: string
  events: EventItem[]
  ddayTasks: TaskItem[]
  onSelectDate: (ymd: string) => void
  onEventClick: (event: EventItem) => void
  onDoubleClickDate: (ymd: string) => void
}

// Figma 147:438 주뷰 — 7일 컬럼 스트립: 날짜번호 + 이벤트 칩, 시간 그리드 없음.
export function WeekStrip({
  weekStart,
  selectedDate,
  today,
  events,
  ddayTasks,
  onSelectDate,
  onEventClick,
  onDoubleClickDate,
}: WeekStripProps) {
  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-[0_1px_1px_rgba(0,0,0,0.05)]">
      <div className="grid grid-cols-7 border-b border-border bg-surface-muted">
        {WEEKDAYS.map((w) => (
          <div key={w} className="px-2 py-2 text-center text-xs font-bold text-text-secondary">
            {w}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {days.map((day, i) => {
          const ymd = toYmd(day)
          const dayEvents = sortByStart(events.filter((e) => eventCoversDate(e, ymd)))
          const dayDdays = ddayTasks.filter((t) => t.dueDate === ymd)
          const overflow = dayEvents.length + dayDdays.length - MAX_CELL_ITEMS
          const selected = ymd === selectedDate
          const isToday = ymd === today

          return (
            <div
              key={ymd}
              onClick={() => onSelectDate(ymd)}
              onDoubleClick={() => onDoubleClickDate(ymd)}
              className={cx(
                'flex min-h-36 cursor-pointer flex-col items-stretch gap-1 p-2 transition duration-150 hover:bg-surface-muted',
                (i + 1) % 7 !== 0 && 'border-r border-border',
                selected && 'outline-2 -outline-offset-2 outline-accent',
              )}
            >
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => onSelectDate(ymd)}
                  aria-pressed={selected}
                  aria-label={`${day.getDate()} ${ymd} 선택`}
                  className={cx(
                    'inline-flex h-6 w-6 items-center justify-center rounded-full text-sm font-bold',
                    isToday ? 'bg-accent-deep text-white' : 'text-text',
                  )}
                >
                  {day.getDate()}
                </button>
                {isToday && (
                  <span className="rounded bg-accent-soft px-1 py-0.5 text-[10px] font-bold text-on-tint">
                    오늘
                  </span>
                )}
              </div>
              {dayEvents.slice(0, MAX_CELL_ITEMS).map((e) => (
                <button
                  key={e.id}
                  type="button"
                  onClick={(ev) => {
                    ev.stopPropagation()
                    onEventClick(e)
                  }}
                  className={cx(
                    'block w-full truncate rounded px-1.5 py-1 text-left text-[11px] font-semibold',
                    CHIP_STYLES[e.source],
                  )}
                >
                  {e.title}
                </button>
              ))}
              {dayDdays.slice(0, Math.max(0, MAX_CELL_ITEMS - dayEvents.length)).map((t) => (
                <span
                  key={`task-${t.id}`}
                  className={cx(
                    'block w-full truncate rounded px-1.5 py-1 text-[11px] font-semibold',
                    CHIP_STYLES.dday,
                  )}
                >
                  {t.title} {formatDday(ddayOffset(t.dueDate, today))}
                </span>
              ))}
              {overflow > 0 && (
                <span className="px-1 text-[11px] font-semibold text-text-secondary">
                  +{overflow} more
                </span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
