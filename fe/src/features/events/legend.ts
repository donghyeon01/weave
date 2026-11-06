import type { EventItem } from './types'

// 범례 카테고리(Figma 확정) — 칩 색상은 여기 상수가 유일한 원천이다.
export const CHIP_STYLES: Record<EventItem['source'] | 'dday', string> = {
  manual: 'bg-primary text-primary-dark',
  proposal: 'bg-accent-soft text-accent-deep',
  dday: 'bg-danger text-danger-text',
} as const

export const SOURCE_LABEL: Record<EventItem['source'], string> = {
  manual: '개인 및 스터디',
  proposal: '스마트 조율 약속',
} as const

export const LEGEND_ITEMS = [
  { key: 'manual', label: '개인 및 스터디', swatch: 'bg-primary' },
  { key: 'proposal', label: '스마트 조율 약속', swatch: 'bg-accent-soft' },
  { key: 'dday', label: '시험 및 주요 D-Day', swatch: 'bg-danger' },
] as const
