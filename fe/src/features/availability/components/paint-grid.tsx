import { useCallback, useEffect, useRef, useState } from 'react'
import { cx } from '../../../utils/cx'
import { dayOfWeek, mondayIndex, slotAtomKeys, visualSlots, type CellKey, type CellStep } from '../utils'

interface PaintGridProps {
  mode: 'TIME' | 'DATE'
  /** TIME: 범위 날짜 목록 · DATE: 동일 (주 단위 행 구성용) */
  dates: string[]
  /** TIME 전용 표시 셀 크기 — 데이터는 항상 15분 원자 */
  stepMin: CellStep
  /** TIME: CellKey("yyyy-MM-dd@분") 집합 · DATE: "yyyy-MM-dd" 집합 */
  selected: Set<string>
  onChange: (next: Set<string>) => void
}

type DragState = {
  anchorSlot: number
  anchorDate: number
  curSlot: number
  curDate: number
  mode: 'paint' | 'erase'
} | null

function sameSet<T>(a: Set<T>, b: Set<T>): boolean {
  if (a.size !== b.size) return false
  for (const v of a) if (!b.has(v)) return false
  return true
}

// 표시 셀 상태 — 원자 셀 집합에서 유도 (전체 칠함/부분 칠함/빈 셀)
type VisualState = 'empty' | 'partial' | 'full'

function visualStateOf(keys: CellKey[], selected: Set<string>): VisualState {
  let filled = 0
  for (const k of keys) if (selected.has(k)) filled++
  if (filled === 0) return 'empty'
  return filled === keys.length ? 'full' : 'partial'
}

const CELL_BASE =
  'flex h-7 w-14 shrink-0 items-center justify-center rounded-[4px] border text-[10px] font-semibold ' +
  'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring ' +
  'disabled:cursor-not-allowed disabled:opacity-50'

function cellClass(state: VisualState, preview: 'paint' | 'erase' | null): string {
  if (preview === 'paint') return `${CELL_BASE} border-primary bg-primary text-on-tint`
  if (preview === 'erase') return `${CELL_BASE} border-danger bg-danger/50 text-on-danger`
  switch (state) {
    case 'full':
      return `${CELL_BASE} border-transparent bg-tint-mint text-text`
    case 'partial':
      return `${CELL_BASE} border-tint-mint bg-tint-mint/40 text-text`
    default:
      return `${CELL_BASE} border-border/60 bg-surface text-text-muted hover:bg-surface-muted`
  }
}

/** TIME 모드 — 행=시간 슬롯(세로 스크롤) × 열=날짜(가로 스크롤), 드래그 사각 선택 */
function TimeGrid({
  dates,
  stepMin,
  selected,
  onChange,
}: {
  dates: string[]
  stepMin: CellStep
  selected: Set<string>
  onChange: (next: Set<string>) => void
}) {
  const slots = visualSlots(stepMin)
  const dragRef = useRef<DragState>(null)
  const [drag, setDrag] = useState<DragState>(null)
  const [focus, setFocus] = useState({ slot: 0, date: 0 })
  const cellRefs = useRef(new Map<string, HTMLButtonElement>())

  const refKey = (d: number, s: number) => `${d}:${s}`

  const inRect = useCallback(
    (d: number, s: number, dr: NonNullable<DragState>) =>
      d >= Math.min(dr.anchorDate, dr.curDate) &&
      d <= Math.max(dr.anchorDate, dr.curDate) &&
      s >= Math.min(dr.anchorSlot, dr.curSlot) &&
      s <= Math.max(dr.anchorSlot, dr.curSlot),
    [],
  )

  const applyRect = useCallback(
    (dr: NonNullable<DragState>) => {
      const next = new Set(selected)
      for (let d = Math.min(dr.anchorDate, dr.curDate); d <= Math.max(dr.anchorDate, dr.curDate); d++) {
        for (let s = Math.min(dr.anchorSlot, dr.curSlot); s <= Math.max(dr.anchorSlot, dr.curSlot); s++) {
          const keys = slotAtomKeys(dates[d], slots[s].minute, stepMin)
          for (const k of keys) {
            if (dr.mode === 'paint') next.add(k)
            else next.delete(k)
          }
        }
      }
      if (!sameSet(next, selected)) onChange(next)
    },
    [dates, onChange, selected, slots, stepMin],
  )

  // 드래그 종료 — pointerup에서 커밋, pointercancel(터치 스크롤 인수 등)은 폐기
  useEffect(() => {
    const up = () => {
      const dr = dragRef.current
      dragRef.current = null
      setDrag(null)
      if (dr) applyRect(dr)
    }
    const cancel = () => {
      dragRef.current = null
      setDrag(null)
    }
    window.addEventListener('pointerup', up)
    window.addEventListener('pointercancel', cancel)
    return () => {
      window.removeEventListener('pointerup', up)
      window.removeEventListener('pointercancel', cancel)
    }
  }, [applyRect])

  const startDrag = (d: number, s: number) => (e: React.PointerEvent) => {
    e.preventDefault()
    const keys = slotAtomKeys(dates[d], slots[s].minute, stepMin)
    const hasAny = keys.some((k) => selected.has(k))
    const dr: DragState = { anchorSlot: s, anchorDate: d, curSlot: s, curDate: d, mode: hasAny ? 'erase' : 'paint' }
    dragRef.current = dr
    setDrag(dr)
    cellRefs.current.get(refKey(d, s))?.focus()
  }

  const moveDrag = (d: number, s: number) => () => {
    const dr = dragRef.current
    if (!dr) return
    if (dr.curSlot === s && dr.curDate === d) return
    const next = { ...dr, curSlot: s, curDate: d }
    dragRef.current = next
    setDrag(next)
  }

  const toggleCell = (d: number, s: number) => {
    const keys = slotAtomKeys(dates[d], slots[s].minute, stepMin)
    const state = visualStateOf(keys, selected)
    const next = new Set(selected)
    for (const k of keys) {
      if (state === 'full') next.delete(k)
      else next.add(k)
    }
    onChange(next)
  }

  const moveFocus = (d: number, s: number) => {
    const cd = Math.max(0, Math.min(dates.length - 1, d))
    const cs = Math.max(0, Math.min(slots.length - 1, s))
    setFocus({ slot: cs, date: cd })
    cellRefs.current.get(refKey(cd, cs))?.focus()
  }

  const onCellKeyDown = (d: number, s: number) => (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowRight':
        e.preventDefault()
        moveFocus(d + 1, s)
        break
      case 'ArrowLeft':
        e.preventDefault()
        moveFocus(d - 1, s)
        break
      case 'ArrowDown':
        e.preventDefault()
        moveFocus(d, s + 1)
        break
      case 'ArrowUp':
        e.preventDefault()
        moveFocus(d, s - 1)
        break
      case 'Home':
        e.preventDefault()
        moveFocus(0, s)
        break
      case 'End':
        e.preventDefault()
        moveFocus(dates.length - 1, s)
        break
      case 'Enter':
      case ' ':
        e.preventDefault()
        toggleCell(d, s)
        break
    }
  }

  return (
    <div className="overflow-auto rounded-xl border border-border bg-surface" style={{ maxHeight: 520 }}>
      <div
        role="grid"
        aria-label="가능 시간 선택 그리드 — 행은 시간, 열은 날짜"
        aria-rowcount={slots.length + 1}
        aria-colcount={dates.length + 1}
        className="w-max min-w-full"
      >
        {/* 열 헤더 — 날짜 */}
        <div role="row" className="sticky top-0 z-10 flex bg-surface">
          <div className="w-14 shrink-0" aria-hidden="true" />
          {dates.map((date) => (
            <div
              key={date}
              role="columnheader"
              className="flex h-9 w-14 shrink-0 flex-col items-center justify-center border-b border-border text-[10px] font-bold text-text-secondary"
            >
              <span>{Number(date.slice(8))}</span>
              <span className="font-medium">{dayOfWeek(date)}</span>
            </div>
          ))}
        </div>
        {/* 시간 행 */}
        {slots.map((slot, s) => (
          <div key={slot.minute} role="row" className="flex">
            <div
              role="rowheader"
              className="sticky left-0 z-[5] flex h-7 w-14 shrink-0 items-center justify-center border-b border-border bg-surface text-[10px] font-semibold text-text-muted"
            >
              {slot.label}
            </div>
            {dates.map((date, d) => {
              const keys = slotAtomKeys(date, slot.minute, stepMin)
              const state = visualStateOf(keys, selected)
              const preview =
                drag && inRect(d, s, drag)
                  ? drag.mode === 'erase' && state === 'empty'
                    ? null
                    : drag.mode
                  : null
              const isFocus = focus.slot === s && focus.date === d
              return (
                <button
                  key={date}
                  ref={(el) => {
                    if (el) cellRefs.current.set(refKey(d, s), el)
                    else cellRefs.current.delete(refKey(d, s))
                  }}
                  type="button"
                  role="gridcell"
                  aria-selected={state !== 'empty'}
                  aria-label={`${date} ${slot.label}`}
                  tabIndex={isFocus ? 0 : -1}
                  className={cellClass(state, preview)}
                  onPointerDown={startDrag(d, s)}
                  onPointerEnter={moveDrag(d, s)}
                  onFocus={() => setFocus({ slot: s, date: d })}
                  onKeyDown={onCellKeyDown(d, s)}
                />
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

/** DATE 모드 — 주(월~일) 단위 행, 날짜 셀 탭 토글 */
function DateGrid({
  dates,
  selected,
  onChange,
}: {
  dates: string[]
  selected: Set<string>
  onChange: (next: Set<string>) => void
}) {
  const lead = mondayIndex(dates[0])
  const cells: (string | null)[] = [
    ...Array.from({ length: lead }, () => null),
    ...dates,
    // 마지막 주 채우기 — 7열 정렬 유지용 trailing 공백
    ...Array.from({ length: (7 - ((lead + dates.length) % 7)) % 7 }, () => null),
  ]
  const weeks: (string | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))

  const [focus, setFocus] = useState(0)
  const cellRefs = useRef(new Map<number, HTMLButtonElement>())

  const toggle = (date: string) => {
    const next = new Set(selected)
    if (next.has(date)) next.delete(date)
    else next.add(date)
    onChange(next)
  }

  const moveFocus = (idx: number) => {
    const c = Math.max(0, Math.min(dates.length - 1, idx))
    setFocus(c)
    cellRefs.current.get(c)?.focus()
  }

  const onKeyDown = (idx: number) => (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowRight':
        e.preventDefault()
        moveFocus(idx + 1)
        break
      case 'ArrowLeft':
        e.preventDefault()
        moveFocus(idx - 1)
        break
      case 'ArrowDown':
        e.preventDefault()
        moveFocus(idx + 7)
        break
      case 'ArrowUp':
        e.preventDefault()
        moveFocus(idx - 7)
        break
      case 'Home':
        e.preventDefault()
        moveFocus(0)
        break
      case 'End':
        e.preventDefault()
        moveFocus(dates.length - 1)
        break
      case 'Enter':
      case ' ':
        e.preventDefault()
        toggle(dates[idx])
        break
    }
  }

  let dateIdx = -1
  return (
    <div className="overflow-auto rounded-xl border border-border bg-surface p-3" style={{ maxHeight: 520 }}>
      <div role="grid" aria-label="가능한 날짜 선택 그리드" className="flex flex-col gap-1.5">
        {weeks.map((week, wi) => (
          <div key={wi} role="row" className="grid grid-cols-7 gap-1.5">
            {week.map((date, ci) => {
              if (date === null) {
                return <div key={`b${wi}-${ci}`} aria-hidden="true" />
              }
              dateIdx++
              const idx = dateIdx
              const on = selected.has(date)
              const dayNum = Number(date.slice(8))
              return (
                <button
                  key={date}
                  ref={(el) => {
                    if (el) cellRefs.current.set(idx, el)
                    else cellRefs.current.delete(idx)
                  }}
                  type="button"
                  role="gridcell"
                  aria-selected={on}
                  aria-label={`${date} (${dayOfWeek(date)})`}
                  tabIndex={focus === idx ? 0 : -1}
                  onClick={() => toggle(date)}
                  onFocus={() => setFocus(idx)}
                  onKeyDown={onKeyDown(idx)}
                  className={cx(
                    'flex h-14 flex-col items-center justify-center rounded-xl border-2 text-sm font-bold transition',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
                    on
                      ? 'border-transparent bg-tint-mint text-text'
                      : 'border-border bg-surface text-text hover:bg-surface-muted',
                  )}
                >
                  <span>{dayNum}</span>
                  <span className="text-[10px] font-medium text-text-secondary">
                    {dayOfWeek(date)}
                  </span>
                </button>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

export function PaintGrid(props: PaintGridProps) {
  if (props.mode === 'DATE') {
    return <DateGrid dates={props.dates} selected={props.selected} onChange={props.onChange} />
  }
  return (
    <TimeGrid dates={props.dates} stepMin={props.stepMin} selected={props.selected} onChange={props.onChange} />
  )
}
