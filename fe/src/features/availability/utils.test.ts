import { describe, expect, it } from 'vitest'
import {
  addDays,
  blocksToDates,
  blocksToTimeCells,
  cellKey,
  datesToBlocks,
  dateList,
  dayOfWeek,
  mondayIndex,
  periodLabel,
  slotAtomKeys,
  timeCellsToBlocks,
  visualSlots,
} from './utils'

describe('날짜 유틸', () => {
  it('dateList는 범위 양끝을 포함한다', () => {
    expect(dateList('2025-11-10', '2025-11-12')).toEqual([
      '2025-11-10',
      '2025-11-11',
      '2025-11-12',
    ])
    expect(dateList('2025-11-10', '2025-11-10')).toEqual(['2025-11-10'])
    expect(dateList('2025-11-12', '2025-11-10')).toEqual([])
  })

  it('addDays는 월·연 경계를 넘는다', () => {
    expect(addDays('2025-11-30', 1)).toBe('2025-12-01')
    expect(addDays('2025-12-31', 1)).toBe('2026-01-01')
    expect(addDays('2025-11-10', -1)).toBe('2025-11-09')
  })

  it('dayOfWeek·mondayIndex가 요일을 계산한다', () => {
    expect(dayOfWeek('2025-11-10')).toBe('월') // 2025-11-10은 월요일
    expect(dayOfWeek('2025-11-16')).toBe('일')
    expect(mondayIndex('2025-11-10')).toBe(0) // 월요일 시작 주의 첫 셀
    expect(mondayIndex('2025-11-12')).toBe(2) // 수요일 → 앞에 월·화 공백 2칸
  })

  it('periodLabel은 기간을 한 줄로 요약한다', () => {
    expect(periodLabel('2025-11-10', '2025-11-16')).toBe('11월 10일 (월) – 16일 (일) · 7일')
    expect(periodLabel('2025-11-28', '2025-12-02')).toBe('11월 28일 (금) – 12월 2일 (화) · 5일')
  })
})

describe('TIME 셀 ↔ 블록 변환', () => {
  it('visualSlots·slotAtomKeys는 표시 스텝과 15분 원자를 분리한다', () => {
    expect(visualSlots(60)).toHaveLength(24)
    expect(visualSlots(30)).toHaveLength(48)
    expect(slotAtomKeys('2025-11-10', 540, 60)).toEqual([
      '2025-11-10@540',
      '2025-11-10@555',
      '2025-11-10@570',
      '2025-11-10@585',
    ])
    expect(slotAtomKeys('2025-11-10', 540, 15)).toEqual(['2025-11-10@540'])
  })

  it('blocksToTimeCells는 블록 구간과 겹치는 15분 원자를 채운다', () => {
    const cells = blocksToTimeCells([
      { kind: 'TIME', startAt: '2025-11-10T09:00:00', endAt: '2025-11-10T10:30:00' },
    ])
    // 09:00~10:30 = 90분 = 6개 원자 (540,555,570,585,600,615)
    expect(cells.size).toBe(6)
    expect(cells.has(cellKey('2025-11-10', 540))).toBe(true)
    expect(cells.has(cellKey('2025-11-10', 615))).toBe(true)
    expect(cells.has(cellKey('2025-11-10', 630))).toBe(false)
  })

  it('자정을 넘는 블록은 다음 날짜 원자로 나뉜다', () => {
    const cells = blocksToTimeCells([
      { kind: 'TIME', startAt: '2025-11-10T23:30:00', endAt: '2025-11-11T00:30:00' },
    ])
    expect(cells.has('2025-11-10@1410')).toBe(true) // 23:30
    expect(cells.has('2025-11-10@1425')).toBe(true) // 23:45
    expect(cells.has('2025-11-11@0')).toBe(true) // 00:00
    expect(cells.has('2025-11-11@15')).toBe(true) // 00:15 (00:30 배타 상한까지)
    expect(cells.has('2025-11-11@30')).toBe(false)
  })

  it('timeCellsToBlocks는 연속 원자를 하나의 블록으로 병합하고 endAt은 배타다', () => {
    const cells = new Set(['2025-11-10@540', '2025-11-10@555', '2025-11-10@570'])
    expect(timeCellsToBlocks(cells)).toEqual([
      { kind: 'TIME', startAt: '2025-11-10T09:00:00', endAt: '2025-11-10T09:45:00' },
    ])
  })

  it('떨어진 구간은 별도 블록이 되고 날짜별로 정렬된다', () => {
    const cells = new Set([
      '2025-11-11@600',
      '2025-11-10@600',
      '2025-11-10@660',
    ])
    const blocks = timeCellsToBlocks(cells)
    expect(blocks).toEqual([
      { kind: 'TIME', startAt: '2025-11-10T10:00:00', endAt: '2025-11-10T10:15:00' },
      { kind: 'TIME', startAt: '2025-11-10T11:00:00', endAt: '2025-11-10T11:15:00' },
      { kind: 'TIME', startAt: '2025-11-11T10:00:00', endAt: '2025-11-11T10:15:00' },
    ])
  })

  it('하루 전체를 칠하면 endAt이 익일 00:00으로 롤오버된다', () => {
    const cells = new Set(
      Array.from({ length: 96 }, (_, i) => cellKey('2025-11-10', i * 15)),
    )
    const blocks = timeCellsToBlocks(cells)
    expect(blocks).toHaveLength(1)
    expect(blocks[0].startAt).toBe('2025-11-10T00:00:00')
    expect(blocks[0].endAt).toBe('2025-11-11T00:00:00')
  })

  it('cells→blocks→cells 왕복이 원자 집합을 보존한다', () => {
    const cells = new Set(['2025-11-10@0', '2025-11-10@15', '2025-11-10@705', '2025-11-12@1305'])
    expect(blocksToTimeCells(timeCellsToBlocks(cells))).toEqual(cells)
  })
})

describe('DATE 블록 변환', () => {
  it('날짜 집합은 날짜당 1블록이 되고 정렬된다', () => {
    expect(datesToBlocks(new Set(['2025-11-12', '2025-11-10']))).toEqual([
      { kind: 'DATE', blockDate: '2025-11-10' },
      { kind: 'DATE', blockDate: '2025-11-12' },
    ])
  })

  it('blocksToDates는 DATE 블록만 추출한다', () => {
    const blocks = [
      { kind: 'DATE' as const, blockDate: '2025-11-10' },
      { kind: 'TIME' as const, startAt: '2025-11-10T09:00:00', endAt: '2025-11-10T10:00:00' },
    ]
    expect(blocksToDates(blocks)).toEqual(new Set(['2025-11-10']))
  })
})
