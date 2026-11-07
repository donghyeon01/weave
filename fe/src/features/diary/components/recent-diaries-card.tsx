import { useState } from 'react'
import { useDiaryMonth } from '../api/use-diary-month'
import { cx } from '../../../utils/cx'

// 지난 일기 — 시안 4:366 우측 레일 Card 3: 최근 3건 + "전체보기"로 이번 달 전체 펼침.
export function RecentDiariesCard({
  selectedDate,
  onSelectDate,
}: {
  selectedDate: string
  onSelectDate: (date: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const yearMonth = selectedDate.slice(0, 7)
  const monthQuery = useDiaryMonth(yearMonth)
  const entries = monthQuery.data ?? []
  const visible = expanded ? entries : entries.slice(0, 3)

  return (
    <section className="rounded-2xl border border-border bg-surface p-5 shadow-[0_1px_1px_rgba(0,0,0,0.05)]">
      <div className="flex items-center justify-between">
        <h3 className="font-display text-lg">지난 일기</h3>
        {entries.length > 3 && (
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="text-sm font-bold text-accent hover:underline"
          >
            {expanded ? '접기' : '전체보기'}
          </button>
        )}
      </div>
      {entries.length === 0 && !monthQuery.isPending && (
        <p className="mt-3 text-sm font-medium text-text-muted">이번 달 일기가 아직 없어요.</p>
      )}
      <ul className="mt-3 flex flex-col">
        {visible.map((d) => {
          const date = new Date(`${d.date}T00:00:00`)
          const label = date.toLocaleDateString('ko-KR', {
            month: 'long',
            day: '2-digit',
            weekday: 'short',
          })
          return (
            <li key={d.date}>
              <button
                type="button"
                onClick={() => onSelectDate(d.date)}
                className={cx(
                  'flex w-full items-baseline gap-3 border-t border-border py-3 text-left',
                  d.date === selectedDate && 'bg-surface-muted/50',
                )}
              >
                <span className="shrink-0 text-xs font-bold text-accent">{label}</span>
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-text">
                  {d.title ?? '(제목 없음)'}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
