import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '../../../components/ui/button'
import { InlineError } from '../../../components/ui/inline-error'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
import { usePreferredWindows } from '../api/use-preferred-windows'
import { usePutPreferredWindows } from '../api/use-put-preferred-windows'
import type { PreferredWindow } from '../types'

const cardClass = 'rounded-2xl border border-border bg-surface p-6 shadow-card-sm'

// 요일 선택지 — ISO 요일(월=1~일=7), 표시 순서 월~일 (계약 §3.12)
const DAY_OPTIONS = [
  { iso: 1, label: '월' },
  { iso: 2, label: '화' },
  { iso: 3, label: '수' },
  { iso: 4, label: '목' },
  { iso: 5, label: '금' },
  { iso: 6, label: '토' },
  { iso: 7, label: '일' },
] as const

const MAX_WINDOWS = 30

const inputCls =
  'rounded-lg border-2 border-input bg-surface px-2 py-1.5 text-sm outline-none transition duration-150 focus:border-accent'

function dayLabel(iso: number): string {
  return DAY_OPTIONS.find((d) => d.iso === iso)?.label ?? ''
}

// 같은 요일 안에서 [start,end) 구간이 겹치는지 — 끝=시작 맞닿음은 허용 (계약 §3.12)
function hasOverlap(rows: PreferredWindow[]): boolean {
  const byDay = new Map<number, { startTime: string; endTime: string }[]>()
  for (const r of rows) {
    const list = byDay.get(r.dayOfWeek) ?? []
    list.push(r)
    byDay.set(r.dayOfWeek, list)
  }
  for (const list of byDay.values()) {
    const sorted = [...list].sort((a, b) => a.startTime.localeCompare(b.startTime))
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].startTime < sorted[i - 1].endTime) return true
    }
  }
  return false
}

function sameWindows(a: PreferredWindow[], b: PreferredWindow[]): boolean {
  const norm = (rows: PreferredWindow[]) =>
    [...rows]
      .map((r) => `${r.dayOfWeek}|${r.startTime}|${r.endTime}`)
      .sort()
      .join(',')
  return norm(a) === norm(b)
}

/** "선호 시간대" 카드 — 요일별 선호 시간 편집·저장 (계약 §3.12 preferred-windows, Figma 236:1583) */
export function PreferredWindowsCard() {
  const query = usePreferredWindows()
  const put = usePutPreferredWindows()
  // 로컬 편집 드래프트 — 서버 값이 도착하기 전과 PUT 실패 시에도 편집 내용을 유지한다
  const [rows, setRows] = useState<PreferredWindow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)
  // 최초 로드 시 1회만 드래프트 초기화 — 이후 리페치·PUT 응답으로 편집 내용을 덮지 않는다
  const initialized = useRef(false)

  const saved = query.data?.windows
  useEffect(() => {
    if (initialized.current || saved === undefined) return
    initialized.current = true
    setRows([...saved])
  }, [saved])

  const dirty = useMemo(
    () => rows !== null && saved !== undefined && !sameWindows(rows, saved),
    [rows, saved],
  )

  const update = (index: number, patch: Partial<PreferredWindow>) => {
    setRows((prev) =>
      prev === null ? prev : prev.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    )
    setError(null)
  }

  const addRow = () => {
    setRows((prev) =>
      prev === null || prev.length >= MAX_WINDOWS
        ? prev
        : [...prev, { dayOfWeek: 1, startTime: '19:00', endTime: '21:00' }],
    )
    setError(null)
  }

  const removeRow = (index: number) => {
    setRows((prev) => (prev === null ? prev : prev.filter((_, i) => i !== index)))
    setError(null)
  }

  const save = () => {
    if (rows === null) return
    setError(null)
    if (rows.length > MAX_WINDOWS) {
      setError(`선호 시간대는 최대 ${MAX_WINDOWS}개까지 등록할 수 있어요.`)
      return
    }
    if (rows.some((r) => r.startTime === '' || r.endTime === '')) {
      setError('시작·종료 시간을 모두 입력해 주세요.')
      return
    }
    if (rows.some((r) => r.startTime >= r.endTime)) {
      setError('시작 시간이 종료 시간보다 빨라야 해요.')
      return
    }
    if (hasOverlap(rows)) {
      setError('같은 요일의 선호 시간이 겹쳐요. 끝나는 시간과 시작 시간이 같으면 괜찮아요.')
      return
    }
    put.mutate(rows, {
      onSuccess: (res) => {
        // 서버가 정렬·정규화한 결과로 드래프트 동기화
        setRows([...res.windows])
        setToast({ variant: 'success', message: '선호 시간대를 저장했어요' })
      },
      onError: (err) => {
        // 400 AVAILABILITY-INVALID — 편집 내용은 그대로 두고 인라인 에러만 표시한다
        setError(
          err instanceof ApiError && err.status === 400
            ? '같은 요일의 시간이 겹치거나 형식이 올바르지 않아요. 시간을 확인해 주세요.'
            : '저장하지 못했어요. 다시 시도해 주세요.',
        )
      },
    })
  }

  return (
    <section className={cardClass}>
      <h2 className="font-display text-xl">선호 시간대</h2>
      <p className="mt-1 text-xs font-medium text-text-secondary">
        스케줄 추천의 정렬에만 반영돼요 — 가능 시간 블록을 만들지는 않아요. 요일별로 선호하는
        시간을 HH:mm 형식으로 등록하세요.
      </p>

      {query.isError && saved === undefined ? (
        <p className="mt-4 text-sm text-text-secondary">선호 시간대를 불러오지 못했어요.</p>
      ) : query.isPending || rows === null ? (
        <SkeletonRow rows={3} rowClassName="h-10" className="mt-4" />
      ) : (
        <>
          {rows.length === 0 ? (
            <p className="mt-4 text-sm font-medium text-text-secondary">
              등록된 선호 시간대가 없어요. 추가하면 추천 정렬에 반영돼요.
            </p>
          ) : (
            <ul className="mt-4 flex flex-col gap-2">
              {rows.map((row, i) => (
                <li
                  key={i}
                  className="flex items-center gap-2 rounded-xl border border-border bg-background p-2.5"
                >
                  <select
                    value={row.dayOfWeek}
                    onChange={(e) => update(i, { dayOfWeek: Number(e.target.value) })}
                    disabled={put.isPending}
                    aria-label={`${i + 1}번 선호 시간 요일`}
                    className={cx(inputCls, 'font-bold text-text')}
                  >
                    {DAY_OPTIONS.map((d) => (
                      <option key={d.iso} value={d.iso}>
                        {d.label}요일
                      </option>
                    ))}
                  </select>
                  <input
                    type="time"
                    value={row.startTime}
                    onChange={(e) => update(i, { startTime: e.target.value })}
                    disabled={put.isPending}
                    aria-label={`${dayLabel(row.dayOfWeek)}요일 선호 시작 시간`}
                    className={cx(inputCls, 'text-text')}
                  />
                  <span className="text-xs font-medium text-text-muted">~</span>
                  <input
                    type="time"
                    value={row.endTime}
                    onChange={(e) => update(i, { endTime: e.target.value })}
                    disabled={put.isPending}
                    aria-label={`${dayLabel(row.dayOfWeek)}요일 선호 종료 시간`}
                    className={cx(inputCls, 'text-text')}
                  />
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => removeRow(i)}
                    disabled={put.isPending}
                    aria-label={`${dayLabel(row.dayOfWeek)}요일 ${row.startTime} 선호 시간 삭제`}
                  >
                    삭제
                  </Button>
                </li>
              ))}
            </ul>
          )}

          {error !== null && (
            <div className="mt-3">
              <InlineError message={error} />
            </div>
          )}

          <div className="mt-4 flex items-center justify-between gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={addRow}
              disabled={rows.length >= MAX_WINDOWS || put.isPending}
            >
              + 시간대 추가
            </Button>
            <div className="flex items-center gap-3">
              <span className="text-xs font-medium text-text-muted">
                {dirty ? '저장하지 않은 변경이 있어요' : '변경 사항 없음'}
              </span>
              <Button
                variant="primary"
                size="sm"
                onClick={save}
                disabled={!dirty || put.isPending}
              >
                {put.isPending ? '저장 중…' : '저장'}
              </Button>
            </div>
          </div>
        </>
      )}
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </section>
  )
}
