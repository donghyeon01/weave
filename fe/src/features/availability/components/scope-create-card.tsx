import { useState } from 'react'
import type { FormEvent } from 'react'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
import { useCreateScope } from '../api/use-scopes'
import type { AvailabilityMode, AvailabilityScopeDetail } from '../types'
import { todayYmd } from '../utils'

const cardClass = 'rounded-2xl border border-border bg-surface p-6 shadow-card-sm'

interface ScopeCreateCardProps {
  onCreated: (scope: AvailabilityScopeDetail) => void
}

/** "새 범위" 카드 — mode(TIME/DATE)·기간 선택 → INPUT scope 생성 (api-contract §3.12) */
export function ScopeCreateCard({ onCreated }: ScopeCreateCardProps) {
  const createScope = useCreateScope()
  const [mode, setMode] = useState<AvailabilityMode>('TIME')
  const [rangeStart, setRangeStart] = useState(todayYmd())
  const [rangeEnd, setRangeEnd] = useState(todayYmd())
  const [error, setError] = useState<string | null>(null)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!rangeStart || !rangeEnd) {
      setError('시작일과 종료일을 선택해 주세요.')
      return
    }
    if (rangeStart > rangeEnd) {
      setError('시작일이 종료일보다 늦을 수 없어요.')
      return
    }
    createScope.mutate(
      { mode, rangeStart, rangeEnd },
      {
        onSuccess: (scope) => onCreated(scope),
        onError: (err) => {
          if (err instanceof ApiError && err.code === 'AVAILABILITY-SCOPE-CONFLICT') {
            setError('같은 종류의 범위가 이미 이 기간과 겹쳐요. 기존 범위를 수정해 주세요.')
          } else {
            setError('범위를 만들지 못했어요. 다시 시도해 주세요.')
          }
        },
      },
    )
  }

  return (
    <section className={cardClass}>
      <h2 className="font-display text-xl">새 범위</h2>
      <form onSubmit={submit} className="mt-4 flex flex-col gap-3">
        <div
          role="group"
          aria-label="범위 종류"
          className="grid grid-cols-2 gap-1 rounded-xl bg-surface-muted p-1"
        >
          {(
            [
              { value: 'TIME', label: '시간대' },
              { value: 'DATE', label: '날짜' },
            ] as const
          ).map((opt) => (
            <button
              key={opt.value}
              type="button"
              aria-pressed={mode === opt.value}
              onClick={() => setMode(opt.value)}
              className={cx(
                'rounded-lg px-3 py-1.5 text-sm font-bold transition',
                'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring',
                mode === opt.value ? 'bg-surface text-text shadow-sm' : 'text-text-secondary',
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>
        <label className="block text-xs font-bold text-text-secondary">
          시작일
          <input
            type="date"
            value={rangeStart}
            onChange={(e) => setRangeStart(e.target.value)}
            aria-label="시작일"
            className="mt-1 w-full rounded-xl border-2 border-border bg-surface px-3 py-2 text-sm font-medium text-text outline-none focus:outline-2 focus:outline-solid focus:outline-ring"
          />
        </label>
        <label className="block text-xs font-bold text-text-secondary">
          종료일
          <input
            type="date"
            value={rangeEnd}
            onChange={(e) => setRangeEnd(e.target.value)}
            aria-label="종료일"
            className="mt-1 w-full rounded-xl border-2 border-border bg-surface px-3 py-2 text-sm font-medium text-text outline-none focus:outline-2 focus:outline-solid focus:outline-ring"
          />
        </label>
        {error !== null && (
          <p role="alert" className="text-xs font-semibold text-danger-text">
            {error}
          </p>
        )}
        <Button type="submit" variant="ghost" size="sm" disabled={createScope.isPending}>
          {createScope.isPending ? '만드는 중…' : '범위 만들기'}
        </Button>
      </form>
    </section>
  )
}

export function ScopeModeBadge({ mode }: { mode: AvailabilityMode }) {
  return <Badge variant="default">{mode === 'TIME' ? '시간대' : '날짜'}</Badge>
}
