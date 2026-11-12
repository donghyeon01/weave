import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PaintGrid } from './paint-grid'
import type { CellStep } from '../utils'

function Harness({
  mode,
  dates,
  stepMin = 30,
  initial = new Set<string>(),
}: {
  mode: 'TIME' | 'DATE'
  dates: string[]
  stepMin?: CellStep
  initial?: Set<string>
}) {
  const [selected, setSelected] = useState<Set<string>>(initial)
  return (
    <PaintGrid mode={mode} dates={dates} stepMin={stepMin} selected={selected} onChange={setSelected} />
  )
}

describe('TIME 페인트 그리드', () => {
  const dates = ['2025-11-10', '2025-11-11']

  it('role=grid와 gridcell·행/열 헤더를 렌더한다', () => {
    render(<Harness mode="TIME" dates={dates} />)
    expect(screen.getByRole('grid')).toBeInTheDocument()
    expect(screen.getAllByRole('gridcell')).toHaveLength(48 * 2) // 30분 × 2일
    expect(screen.getAllByRole('columnheader')).toHaveLength(2)
    expect(screen.getAllByRole('rowheader').length).toBeGreaterThan(0)
  })

  it('셀 클릭은 15분 원자 2개(30분 표시 셀)를 선택한다', async () => {
    render(<Harness mode="TIME" dates={dates} />)
    const cell = screen.getByRole('gridcell', { name: '2025-11-10 09:00' })
    expect(cell).toHaveAttribute('aria-selected', 'false')
    await userEvent.click(cell)
    expect(cell).toHaveAttribute('aria-selected', 'true')
    // 30분 셀 = 15분 원자 2개 — 재클릭하면 해제
    await userEvent.click(cell)
    expect(cell).toHaveAttribute('aria-selected', 'false')
  })

  it('15분 스텝에서는 셀 하나가 원자 하나다', () => {
    render(<Harness mode="TIME" dates={['2025-11-10']} stepMin={15} />)
    expect(screen.getAllByRole('gridcell')).toHaveLength(96)
  })

  it('키보드 Enter로 토글하고 방향키로 이동한다', async () => {
    render(<Harness mode="TIME" dates={dates} />)
    const first = screen.getByRole('gridcell', { name: '2025-11-10 00:00' })
    first.focus()
    fireEvent.keyDown(first, { key: 'Enter' })
    expect(first).toHaveAttribute('aria-selected', 'true')

    // ArrowDown → 같은 날짜의 다음 시간 슬롯으로 포커스 이동
    fireEvent.keyDown(first, { key: 'ArrowDown' })
    const next = screen.getByRole('gridcell', { name: '2025-11-10 00:30' })
    expect(document.activeElement).toBe(next)
    fireEvent.keyDown(next, { key: 'ArrowRight' })
    expect(document.activeElement).toBe(
      screen.getByRole('gridcell', { name: '2025-11-11 00:30' }),
    )
  })

  it('저장된 블록은 칠한 상태로 표시된다', () => {
    render(
      <Harness
        mode="TIME"
        dates={['2025-11-10']}
        initial={new Set(['2025-11-10@540', '2025-11-10@555'])}
      />,
    )
    expect(screen.getByRole('gridcell', { name: '2025-11-10 09:00' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(screen.getByRole('gridcell', { name: '2025-11-10 09:30' })).toHaveAttribute(
      'aria-selected',
      'false',
    )
  })
})

describe('DATE 페인트 그리드', () => {
  it('날짜 셀 탭 토글과 aria-selected를 지원한다', async () => {
    render(<Harness mode="DATE" dates={['2025-11-10', '2025-11-11', '2025-11-12']} />)
    const cell = screen.getByRole('gridcell', { name: '2025-11-11 (화)' })
    expect(cell).toHaveAttribute('aria-selected', 'false')
    await userEvent.click(cell)
    expect(cell).toHaveAttribute('aria-selected', 'true')
    await userEvent.click(cell)
    expect(cell).toHaveAttribute('aria-selected', 'false')
  })

  it('키보드 방향키·Enter로 탐색·토글한다', () => {
    render(<Harness mode="DATE" dates={['2025-11-10', '2025-11-11', '2025-11-12']} />)
    const first = screen.getByRole('gridcell', { name: '2025-11-10 (월)' })
    first.focus()
    fireEvent.keyDown(first, { key: 'ArrowRight' })
    const second = screen.getByRole('gridcell', { name: '2025-11-11 (화)' })
    expect(document.activeElement).toBe(second)
    fireEvent.keyDown(second, { key: 'Enter' })
    expect(second).toHaveAttribute('aria-selected', 'true')
  })
})
