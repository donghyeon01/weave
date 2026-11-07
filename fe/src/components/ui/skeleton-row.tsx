import { cx } from '../../utils/cx'

interface SkeletonRowProps {
  rows?: number
  rowClassName?: string
  className?: string
}

// Figma state/skeleton-row 116:315 — surface-muted 채움 바. 로딩 플레이스홀더.
// rowClassName으로 행 높이를 조정한다 — 화면 상태 프레임이 요구하는 블록 크기에 맞춘다.
export function SkeletonRow({ rows = 1, rowClassName = 'h-18', className }: SkeletonRowProps) {
  return (
    <div role="status" aria-label="불러오는 중" className={cx('flex flex-col gap-3', className)}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} aria-hidden="true" className={cx('animate-pulse rounded-xl bg-surface-muted', rowClassName)} />
      ))}
    </div>
  )
}
