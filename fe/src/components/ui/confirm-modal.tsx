import type { ReactNode } from 'react'
import { Button } from './button'
import { Modal } from './modal'

interface ConfirmModalProps {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  description?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  pending?: boolean
}

// patterns.md 확인 매트릭스 — 삭제·취소·거절 등 파괴 행동은 전부 이 모달을 경유한다.
// 확인 버튼은 tone에 따라 primary(로그아웃 등) 또는 danger(삭제·탈퇴)를 쓴다.
export function ConfirmModal({
  open,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel,
  cancelLabel = '취소',
  tone = 'danger',
  pending = false,
}: ConfirmModalProps) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={<h2 className="text-lg font-bold text-text">{title}</h2>}
    >
      {description ? <div className="text-sm text-text-secondary">{description}</div> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={pending}>
          {cancelLabel}
        </Button>
        <Button variant={tone} onClick={onConfirm} disabled={pending}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
