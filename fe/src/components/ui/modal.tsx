import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

// 마지막 포커스 요소 추적 — Enter 활성화처럼 React 커밋 전에 포커스가 해제되는 경로에서도
// 오픈 직전 호출자를 복원 대상으로 잡기 위해 모듈 로드 시 상시 기록한다.
const lastFocused: { el: HTMLElement | null } = { el: null }
if (typeof document !== 'undefined') {
  document.addEventListener(
    'focusin',
    (e) => {
      // dialog 내부 이동(autoFocus·trap)은 복원 대상이 아니다 — 외부 포커스만 기록한다.
      if (e.target instanceof HTMLElement && e.target.closest('[role="dialog"]') === null) {
        lastFocused.el = e.target
      }
    },
    true,
  )
}

// 모듈 레벨 오픈 스택 — 중첩 모달에서 Escape·Tab 트랩은 최상위 인스턴스만 처리하고,
// body overflow 잠금은 마지막 모달이 닫힐 때 해제한다. (이슈 #3 F7)
const openModals: symbol[] = []

// 퇴출 애니메이션(150ms) 동안 DOM을 유지하기 위한 삼단계 phase
type Phase = 'closed' | 'open' | 'exiting'
const EXIT_MS = 150 // animate-modal-out duration과 일치

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
  // 오픈 직전 포커스 — StrictMode의 setup→cleanup→setup 재실행과 내부 autoFocus 이동에도
  // 덮어쓰지 않도록 최초 1회만 캡처한다.
  const previousRef = useRef<HTMLElement | null>(null)
  // onClose를 deps에 두면 부모 재렌더마다 focus가 재실행돼 포커스를 뺏으므로 ref로 최신값만 참조
  const onCloseRef = useRef(onClose)
  const idRef = useRef(Symbol('modal'))

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  const [phase, setPhase] = useState<Phase>(open ? 'open' : 'closed')
  // effect 안에서 최신 phase를 동기적으로 읽기 위한 미러 — 같은 커밋의 연속 전이 판정용
  const phaseRef = useRef<Phase>(phase)
  const exitTimer = useRef<ReturnType<typeof setTimeout>>(null)
  const mounted = phase !== 'closed'

  // open prop 전이를 phase로 변환 — 닫힘 요청 시 즉시 언마운트하지 않고
  // exiting(150ms) 후 closed로 내린다. 퇴출 중 재오픈하면 타이머를 해제하고 open 복귀.
  // useLayoutEffect — 페인트 전 동기 재렌더로 open→portal 표시가 1프레임 밀리지 않게 한다.
  useLayoutEffect(() => {
    if (open) {
      if (exitTimer.current !== null) {
        clearTimeout(exitTimer.current)
        exitTimer.current = null
      }
      phaseRef.current = 'open'
      setPhase('open')
      return
    }
    if (phaseRef.current === 'closed') return
    phaseRef.current = 'exiting'
    setPhase('exiting')
    exitTimer.current = setTimeout(() => {
      exitTimer.current = null
      phaseRef.current = 'closed'
      setPhase('closed')
    }, EXIT_MS)
    return () => {
      if (exitTimer.current !== null) clearTimeout(exitTimer.current)
    }
  }, [open])

  // 포커스 캡처·이동은 useLayoutEffect — paint 전에 실행돼 호출자 포커스를 잡는다.
  // activeElement가 이미 BODY로 해제된 경우(Enter 활성화)는 마지막 포커스 추적으로 복구한다.
  // mounted 기준 — open→exiting 전이에서는 재실행되지 않아 퇴출 동안 잠금·트랩이 유지된다.
  useLayoutEffect(() => {
    if (!mounted) return
    if (previousRef.current === null) {
      const active = document.activeElement
      const candidate = active instanceof HTMLElement && active !== document.body
          && !cardRef.current?.contains(active)
          ? active
          : lastFocused.el
      previousRef.current =
        candidate !== null && candidate.isConnected && candidate !== document.body
          ? candidate
          : null
    }
    cardRef.current?.focus()

    const id = idRef.current
    openModals.push(id)
    // 하위 모달의 리스너도 document에서 발화한다 — 최상위가 아니면 아무것도 하지 않는다.
    const isTop = () => openModals[openModals.length - 1] === id

    const onKeyDown = (e: KeyboardEvent) => {
      if (!isTop()) return
      if (e.key === 'Escape') {
        onCloseRef.current()
        return
      }
      // aria-modal=true 약속 — Tab은 다이얼로그 내부로만 순환한다.
      // 포털은 body 끝에 렌더돼 카드에서 벗어난 Tab이 배경 콘텐츠로 빠질 수 있다.
      if (e.key !== 'Tab' || cardRef.current === null) return
      const card = cardRef.current
      const items = [...card.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      )].filter((el) => el.getClientRects().length > 0)
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (first === undefined || last === undefined || active === null) return
      if (e.shiftKey ? active === first || active === card : active === last) {
        e.preventDefault()
        ;(e.shiftKey ? last : first).focus()
      } else if (!card.contains(active)) {
        e.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      const i = openModals.indexOf(id)
      if (i !== -1) openModals.splice(i, 1)
      if (openModals.length === 0) document.body.style.overflow = ''
    }
  }, [mounted])

  // 닫힐 때 호출자로 포커스 복귀 — cleanup이 아닌 별도 effect여야 StrictMode의
  // 마운트 cleanup에서 조기 복원되지 않는다. 퇴출 애니메이션이 끝난 mounted→false 시점에 복원.
  useEffect(() => {
    if (mounted) return
    const prev = previousRef.current
    previousRef.current = null
    if (prev !== null && prev.isConnected) {
      prev.focus()
    }
  }, [mounted])

  if (!mounted) return null

  const exiting = phase === 'exiting'

  return createPortal(
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4 backdrop-blur ${exiting ? 'animate-fade-out' : 'animate-fade-in'}`}
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
        className={`relative flex max-h-[calc(100dvh-2rem)] w-full max-w-md flex-col rounded-xl border-2 border-border-strong bg-surface px-6 py-5 shadow-brutal-md outline-none ${exiting ? 'animate-modal-out' : 'animate-modal-in'}`}
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
        <div className="mt-5 min-h-0 overflow-y-auto">{children}</div>
      </div>
    </div>,
    document.body,
  )
}
