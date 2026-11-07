import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface ModalProps {
  open: boolean
  onClose: () => void
  title: ReactNode
  // 중앙 정렬 등 타이틀 블록 추가 클래스 — 닫기 버튼 여백(pr-8)은 유지된다
  titleClassName?: string
  children: ReactNode
}

// modal.md — surface 카드 + 2px border-strong + 12px 반경 + brutal-md, backdrop은 text 40% 디밍 + blur.
// Esc·backdrop 클릭·X로 닫히고 열릴 때 카드로 포커스 이동, 닫히면 이전 요소로 복귀한다.
export function Modal({ open, onClose, title, titleClassName, children }: ModalProps) {
  const titleId = useId()
  const cardRef = useRef<HTMLDivElement>(null)
  // onClose를 deps에 두면 부모 재렌더마다 focus가 재실행돼 포커스를 뺏으므로 ref로 최신값만 참조
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!open) return
    const previous =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    cardRef.current?.focus()

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current()
    }
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
      previous?.focus()
    }
  }, [open])

  if (!open) return null

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4 backdrop-blur"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onCloseRef.current()
      }}
    >
      <div
        ref={cardRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="relative w-full max-w-md rounded-xl border-2 border-border-strong bg-surface px-6 py-5 shadow-brutal-md outline-none"
      >
        <button
          type="button"
          onClick={() => onCloseRef.current()}
          aria-label="닫기"
          className="absolute right-4 top-4 rounded-lg p-1 text-text-secondary transition duration-150 hover:bg-surface-muted"
        >
          <svg viewBox="0 0 24 24" className="h-5 w-5" aria-hidden="true">
            <path
              d="M6 6l12 12M18 6L6 18"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </svg>
        </button>
        <div id={titleId} className={titleClassName ?? 'pr-8'}>
          {title}
        </div>
        <div className="mt-5">{children}</div>
      </div>
    </div>,
    document.body,
  )
}
