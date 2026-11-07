import { cx } from '../../utils/cx'

interface EmptyStateProps {
  title?: string
  description: string
  className?: string
}

// Figma state/empty 116:312 — dashed 카드 + 중앙 제목·안내 문구.
export function EmptyState({
  title = '데이터가 없습니다',
  description,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cx(
        'flex min-h-50 flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-border-strong bg-surface px-12 py-10 text-center',
        className,
      )}
    >
      <p className="text-lg font-bold text-text">{title}</p>
      <p className="text-sm font-medium text-text-secondary">{description}</p>
    </div>
  )
}
