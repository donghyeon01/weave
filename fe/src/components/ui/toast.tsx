import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
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
  info: 'bg-accent-deep text-white',
  success: 'bg-success text-on-success',
  danger: 'bg-danger text-on-danger',
}

const EXIT_MS = 150 // animate-toast-out duration과 일치

// toast.md — 상단 중앙 고정, 동시 1개(부모가 교체 제어), danger는 role=alert+assertive.
// transform 조상 아래 fixed가 깨지는 경우를 대비해 body로 portal한다.
// 수평 중앙 정렬은 -translate-x-1/2 대신 키프레임의 translate(-50%)가 담당한다 —
// translate 속성과 transform 키프레임이 독립적으로 합성돼 이중 오프셋이 생기기 때문.
export function Toast({ variant = 'info', message, duration, onClose }: ToastProps) {
  const ms = duration ?? (variant === 'danger' ? 5000 : 3000)
  const [exiting, setExiting] = useState(false)
  // 자동 소멸과 X 클릭이 겹쳐도 퇴출→onClose는 1회만 예약한다
  const closingRef = useRef(false)
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 퇴출 애니메이션(150ms) 완료 후에야 onClose를 호출 — 부모는 onClose로 언마운트한다.
  const dismiss = useCallback(() => {
    if (closingRef.current) return
    closingRef.current = true
    setExiting(true)
    exitTimer.current = setTimeout(() => {
      exitTimer.current = null
      onClose()
    }, EXIT_MS)
  }, [onClose])

  useEffect(() => {
    const id = setTimeout(dismiss, ms)
    return () => clearTimeout(id)
  }, [ms, dismiss])

  useEffect(
    () => () => {
      if (exitTimer.current !== null) clearTimeout(exitTimer.current)
    },
    [],
  )

  // 토스트 내용(variant·message)이 교체되면 이전 인스턴스의 퇴출 예약을 리셋한다 —
  // keyless 호출부({cond && <Toast/>})에서 같은 위치의 새 토스트가 이전 퇴출 타이머에
  // 제거되는 것을 막는다. layout effect라 교체 커밋의 첫 페인트부터 올바른 상태다.
  useLayoutEffect(() => {
    closingRef.current = false
    if (exitTimer.current !== null) {
      clearTimeout(exitTimer.current)
      exitTimer.current = null
    }
    setExiting(false)
  }, [variant, message])

  const alert = variant === 'danger'
  return createPortal(
    <div
      role={alert ? 'alert' : 'status'}
      aria-live={alert ? 'assertive' : 'polite'}
      className={cx(
        'fixed left-1/2 top-4 z-60 flex items-center gap-3 rounded-xl p-4 text-sm font-semibold shadow-card',
        VARIANTS[variant],
        exiting ? 'animate-toast-out' : 'animate-toast-in',
      )}
    >
      <span>{message}</span>
      <button
        type="button"
        onClick={dismiss}
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
