import type { EventItem, TaskItem } from '../types'
import { cx } from '../../../utils/cx'
import { CHIP_STYLES } from '../legend'
import {
  ddayOffset,
  eventCoversDate,
  formatDday,
  monthGridDays,
  sortByStart,
  toYmd,
} from '../utils'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const
const MAX_CELL_ITEMS = 3

interface CalendarGridProps {
  year: number
  month: number
  selectedDate: string
  today: string
  events: EventItem[]
  ddayTasks: TaskItem[]
  onSelectDate: (ymd: string) => void
  onEventClick: (event: EventItem) => void
}

export function CalendarGrid({
  year,
  month,
  selectedDate,
  today,
  events,
  ddayTasks,
  onSelectDate,
  onEventClick,
}: CalendarGridProps) {
  const days = monthGridDays(year, month)

  return (
    <div className="overflow-hidden rounded-2xl border-2 border-border-strong bg-surface shadow-brutal-sm">
      <div className="grid grid-cols-7 border-b-2 border-border-strong bg-surface-muted">
        {WEEKDAYS.map((w) => (
          <div
            key={w}
            className="px-2 py-2 text-center text-xs font-bold text-text-secondary"
          >
            {w}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {days.map((day, i) => {
          const ymd = toYmd(day)
          const inMonth = day.getMonth() === month
          const dayEvents = sortByStart(events.filter((e) => eventCoversDate(e, ymd)))
          const dayDdays = ddayTasks.filter((t) => t.dueDate === ymd)
          const overflow = dayEvents.length + dayDdays.length - MAX_CELL_ITEMS
          const selected = ymd === selectedDate

          return (
            // 날짜번호·칩은 실제 button(키보드 접근), 빈 영역 클릭도 셀 선택이 되도록 div에 onClick을 둔다.
            <div
              key={ymd}
              onClick={() => onSelectDate(ymd)}
              className={cx(
                'flex min-h-24 cursor-pointer flex-col items-stretch gap-0.5 border-b border-border p-1.5 transition duration-150 hover:bg-surface-muted',
                (i + 1) % 7 !== 0 && 'border-r',
                selected && 'bg-primary/30 outline-2 -outline-offset-2 outline-accent',
                !inMonth && 'bg-surface-muted/50',
              )}
            >
              <button
                type="button"
                onClick={() => onSelectDate(ymd)}
                aria-pressed={selected}
                // 표시 숫자가 aria-label의 공백 토큰으로 존재해야 voice-control("1 클릭")과 일치한다 — "1일"은 한 단어라 불일치 (axe label-content-name-mismatch)
                aria-label={`${day.getDate()} ${ymd} 선택`}
                className={cx(
                  'inline-flex h-6 w-6 items-center justify-center self-start rounded-full text-xs font-bold',
                  ymd === today
                    ? 'bg-accent-deep text-white'
                    : inMonth
                      ? 'text-text'
                      : 'text-text-muted',
                )}
              >
                {day.getDate()}
              </button>
              {dayEvents.slice(0, MAX_CELL_ITEMS).map((e) => (
                <button
                  key={e.id}
                  type="button"
                  onClick={(ev) => {
                    ev.stopPropagation()
                    onEventClick(e)
                  }}
                  className={cx(
                    'block w-full truncate rounded px-1 py-0.5 text-left text-[11px] font-semibold',
                    CHIP_STYLES[e.source],
                  )}
                >
                  {e.title}
                </button>
              ))}
              {dayDdays
                .slice(0, Math.max(0, MAX_CELL_ITEMS - dayEvents.length))
                .map((t) => (
                  <span
                    key={`task-${t.id}`}
                    className={cx(
                      'block w-full truncate rounded px-1 py-0.5 text-[11px] font-semibold',
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
