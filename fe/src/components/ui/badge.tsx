import type { ReactNode } from 'react'
import { cx } from '../../utils/cx'

export type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'accent'

// badge.md — 읽기 전용 필라 칩: pill + 2px border + 14px/600.
// 상태 variant는 배경 * 와 대응 *-text 토큰을 짝으로 쓴다 (파스텔 위 흰색 금지).
const VARIANTS: Record<BadgeVariant, string> = {
  default: 'bg-surface-muted text-text-secondary',
  success: 'bg-success text-success-text',
  warning: 'bg-warning text-warning-text',
  danger: 'bg-danger text-danger-text',
  accent: 'bg-accent-soft text-on-tint',
}

export function Badge({
  variant = 'default',
  className,
  children,
}: {
  variant?: BadgeVariant
  className?: string
  children: ReactNode
}) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 rounded-full border-2 border-border px-3 py-1 text-sm font-bold',
        VARIANTS[variant],
        className,
      )}
    >
      {children}
    </span>
  )
}
