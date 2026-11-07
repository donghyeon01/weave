import type { EventItem } from '../types'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { cx } from '../../../utils/cx'
import { CHIP_STYLES } from '../legend'
import { formatEventTime, sortByStart } from '../utils'
import { EmptyState } from '../../../components/ui/empty-state'

interface DaySidebarProps {
  date: string
  events: EventItem[]
  onEventClick: (event: EventItem) => void
  onAddClick: () => void
}

function formatHeading(ymd: string): string {
  const [y, m, d] = ymd.split('-').map(Number)
  const weekday = ['일', '월', '화', '수', '목', '금', '토'][new Date(y, m - 1, d).getDay()]
  return `${m}월 ${d}일 (${weekday})`
}

export function DaySidebar({ date, events, onEventClick, onAddClick }: DaySidebarProps) {
  const sorted = sortByStart(events)

  return (
    <aside className="w-80 shrink-0 rounded-2xl border border-border bg-surface p-4 shadow-card-sm">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h2 className="font-display text-lg">{formatHeading(date)} 일정</h2>
          {sorted.length > 0 && <Badge>오늘 일정 {sorted.length}개</Badge>}
        </div>
        <Button variant="ghost" size="sm" onClick={onAddClick} aria-label="일정 추가">
          +
        </Button>
      </div>
      {sorted.length === 0 ? (
        <EmptyState
          className="mt-4 min-h-32 px-3 py-6"
          title="일정이 없습니다"
          description="날짜 셀을 더블클릭하면 간편 일정이 추가됩니다"
        />
      ) : (
        <ul className="mt-4 flex flex-col gap-2">
          {sorted.map((e) => (
            <li key={e.id}>
              <button
                type="button"
                onClick={() => onEventClick(e)}
                className="w-full rounded-xl border-2 border-border bg-surface px-3 py-2 text-left transition duration-150 hover:border-border-strong"
              >
                <span
                  className={cx(
                    'inline-block rounded px-1.5 py-0.5 text-[11px] font-semibold',
                    CHIP_STYLES[e.source],
                  )}
                >
                  {e.allDay ? '종일' : formatEventTime(e)}
                </span>
                <span className="mt-1 block truncate text-sm font-semibold text-text">
                  {e.title}
                </span>
                {e.location !== null && e.location !== '' && (
                  <span className="block truncate text-xs text-text-muted">{e.location}</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </aside>
  )
}
