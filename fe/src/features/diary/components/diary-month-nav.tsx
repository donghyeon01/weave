import { Button } from '../../../components/ui/button'
import { cx } from '../../../utils/cx'
import { useDiaryMonth } from '../api/use-diary-month'

interface DiaryMonthNavProps {
  /** "yyyy-MM" */
  yearMonth: string
  selectedDate: string
  onMonthChange: (yearMonth: string) => void
  onSelectDate: (date: string) => void
  onToday: () => void
}

function shiftMonth(yearMonth: string, n: number): string {
  const [y, m] = yearMonth.split('-').map(Number)
  const d = new Date(y, m - 1 + n, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

// 좌측 월 네비게이터 — 기록이 있는 날짜만 목록에 나열한다 (DiarySummary: date+title).
export function DiaryMonthNav({
  yearMonth,
  selectedDate,
  onMonthChange,
  onSelectDate,
  onToday,
}: DiaryMonthNavProps) {
  const monthQuery = useDiaryMonth(yearMonth)
  const entries = [...(monthQuery.data ?? [])].sort((a, b) =>
    b.date.localeCompare(a.date),
  )
  const [y, m] = yearMonth.split('-')

  return (
    <aside className="rounded-2xl border-2 border-border-strong bg-surface p-4 shadow-brutal-sm">
      <div className="flex items-center justify-between">
        <Button variant="ghost" size="sm" onClick={() => onMonthChange(shiftMonth(yearMonth, -1))} aria-label="이전 달">
          ◀
        </Button>
        <span className="font-display text-lg">
          {y}년 {Number(m)}월
        </span>
        <Button variant="ghost" size="sm" onClick={() => onMonthChange(shiftMonth(yearMonth, 1))} aria-label="다음 달">
          ▶
        </Button>
      </div>
      <Button variant="primary" size="sm" fullWidth onClick={onToday} className="mt-3">
        오늘
      </Button>

      {monthQuery.isPending ? (
        <p className="mt-4 text-center text-xs text-text-muted">불러오는 중…</p>
      ) : entries.length === 0 ? (
        <p className="mt-4 text-center text-xs text-text-muted">이 달의 기록이 없어요</p>
      ) : (
        <ul className="mt-4 flex max-h-96 flex-col gap-1 overflow-y-auto">
          {entries.map((entry) => (
            <li key={entry.date}>
              <button
                type="button"
                onClick={() => onSelectDate(entry.date)}
                aria-current={entry.date === selectedDate ? 'date' : undefined}
                className={cx(
                  'w-full truncate rounded-lg px-2 py-1.5 text-left text-sm transition duration-150',
                  entry.date === selectedDate
                    ? 'bg-accent-soft font-semibold text-on-tint'
                    : 'text-text-secondary hover:bg-surface-muted',
                )}
              >
                <span className="mr-1.5 text-xs font-semibold">{entry.date.slice(8)}일</span>
                {entry.title ?? '(제목 없음)'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </aside>
  )
}
