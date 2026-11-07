import { cx } from '../../utils/cx'

interface SkeletonRowProps {
  rows?: number
  className?: string
}

// Figma state/skeleton-row 116:315 — surface-muted 채움 바. 로딩 플레이스홀더.
export function SkeletonRow({ rows = 1, className }: SkeletonRowProps) {
  return (
    <div role="status" aria-label="불러오는 중" className={cx('flex flex-col gap-3', className)}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} aria-hidden="true" className="h-18 animate-pulse rounded-xl bg-surface-muted" />
      ))}
    </div>
  )
}
