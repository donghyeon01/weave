import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { cx } from '../../utils/cx'

export type ToastVariant = 'info' | 'success' | 'danger'

interface ToastProps {
  variant?: ToastVariant
  message: string
  /** 자동 소멸 시간(ms) — 기본 3000, danger 5000 (toast.md) */
  duration?: number
  onClose: () => void
}

const VARIANTS: Record<ToastVariant, string> = {
  info: 'bg-accent text-white',
  success: 'bg-success text-on-success',
  danger: 'bg-danger text-on-danger',
}

// toast.md — 상단 중앙 고정, 동시 1개(부모가 교체 제어), danger는 role=alert+assertive.
// transform 조상 아래 fixed가 깨지는 경우를 대비해 body로 portal한다.
export function Toast({ variant = 'info', message, duration, onClose }: ToastProps) {
  const ms = duration ?? (variant === 'danger' ? 5000 : 3000)

  useEffect(() => {
    const id = setTimeout(onClose, ms)
    return () => clearTimeout(id)
  }, [ms, onClose])

  const alert = variant === 'danger'
  return createPortal(
    <div
      role={alert ? 'alert' : 'status'}
      aria-live={alert ? 'assertive' : 'polite'}
      className={cx(
        'fixed left-1/2 top-4 z-60 flex -translate-x-1/2 items-center gap-3 rounded-xl p-4 text-sm font-semibold shadow-card',
        VARIANTS[variant],
      )}
    >
      <span>{message}</span>
      <button
        type="button"
        onClick={onClose}
        aria-label="알림 닫기"
        className="rounded p-1 transition duration-150 hover:opacity-70"
      >
        <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
          <path
            d="M6 6l12 12M18 6L6 18"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
      </button>
    </div>,
    document.body,
  )
}
