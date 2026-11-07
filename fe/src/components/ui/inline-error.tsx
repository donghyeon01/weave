import { cx } from '../../utils/cx'

interface InlineErrorProps {
  message?: string
  onRetry?: () => void
  className?: string
}

// Figma state/error-inline 116:318 — danger 보더 바 + 밑줄 재시도 액션.
export function InlineError({ message = '불러오지 못했어요', onRetry, className }: InlineErrorProps) {
  return (
    <div
      role="alert"
      className={cx(
        'flex items-center gap-3 rounded-xl border-2 border-danger bg-surface px-5 py-4',
        className,
      )}
    >
      <p className="text-sm font-bold text-danger-text">{message}</p>
      {onRetry !== undefined && (
        <button
          type="button"
          onClick={onRetry}
          className="text-sm font-medium text-text-secondary underline underline-offset-2 hover:text-text"
        >
          다시 불러오기
        </button>
      )}
    </div>
  )
}
