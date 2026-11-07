import type { EventItem } from '../types'
import { cx } from '../../../utils/cx'
import { CHIP_STYLES } from '../legend'
import { sortByStart, timePart } from '../utils'

const GRID_START_H = 6
const GRID_END_H = 23
const ROW_H = 44

interface DayViewProps {
  date: string
  events: EventItem[]
  onEventClick: (event: EventItem) => void
  onDoubleClickTime?: (ymd: string, hour: number) => void
}

function toMinutes(localDateTime: string): number {
  const [h, m] = timePart(localDateTime).split(':').map(Number)
  return h * 60 + m
}

// Figma 147:816 일뷰 — 시간 레일 + 절대 위치 이벤트 블록 (accent 채움).
export function DayView({ date, events, onEventClick, onDoubleClickTime }: DayViewProps) {
  const sorted = sortByStart(events)
  const allDay = sorted.filter((e) => e.allDay)
  const timed = sorted.filter((e) => !e.allDay)
  const gridStart = GRID_START_H * 60
  const hours = Array.from({ length: GRID_END_H - GRID_START_H + 1 }, (_, i) => GRID_START_H + i)

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-[0_1px_1px_rgba(0,0,0,0.05)]">
      {allDay.length > 0 && (
        <div className="flex flex-wrap gap-1.5 border-b border-border bg-surface-muted px-3 py-2">
          {allDay.map((e) => (
            <button
              key={e.id}
              type="button"
              onClick={() => onEventClick(e)}
              className={cx('rounded px-2 py-1 text-[11px] font-semibold', CHIP_STYLES[e.source])}
            >
              종일 · {e.title}
            </button>
          ))}
        </div>
      )}
      <div className="relative flex" style={{ height: (GRID_END_H - GRID_START_H + 1) * ROW_H }}>
        <div className="w-14 shrink-0">
          {hours.map((h) => (
            <div key={h} className="pr-2 text-right text-[11px] font-medium text-text-muted" style={{ height: ROW_H }}>
              {String(h).padStart(2, '0')}:00
            </div>
          ))}
        </div>
        <div
          className="relative flex-1"
          onDoubleClick={(ev) => {
            const rect = ev.currentTarget.getBoundingClientRect()
            const hour = GRID_START_H + Math.floor((ev.clientY - rect.top) / ROW_H)
            onDoubleClickTime?.(date, hour)
          }}
        >
          {hours.map((h) => (
            <div
              key={h}
              className="border-t border-border/60"
              style={{ height: ROW_H }}
            />
          ))}
          {timed.map((e) => {
            const start = Math.max(toMinutes(e.startTime), gridStart)
            const end = Math.min(toMinutes(e.endTime), GRID_END_H * 60 + 60)
            const top = ((start - gridStart) / 60) * ROW_H
            const height = Math.max(((end - start) / 60) * ROW_H, 22)
            return (
              <button
                key={e.id}
                type="button"
                onClick={() => onEventClick(e)}
                className="absolute left-1 right-2 flex flex-col items-start justify-center gap-0.5 overflow-hidden rounded-lg bg-accent-deep px-2 text-left shadow-sm transition duration-150 hover:brightness-105"
                style={{ top, height }}
              >
                <span className="truncate text-[13px] font-bold text-surface">{e.title}</span>
                {height >= 36 && (
                  <span className="text-[11px] font-medium text-surface/85">
                    {timePart(e.startTime)}-{timePart(e.endTime)}
                  </span>
                )}
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
