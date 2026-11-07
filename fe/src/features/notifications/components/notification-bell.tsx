import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { NotificationItem } from '../types'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { useMarkRead, useNotifications, useReadAll } from '../api/use-notifications'
import { useRespondProposal } from '../../scheduling/api/use-proposal-mutations'

// §3.7 payload는 type별 — 공통 필드를 최소 타입으로만 읽고 나머지는 무시한다.
function readTitle(item: NotificationItem): string {
  const p = item.payload
  if (item.type === 'FRIEND_REQUEST' || item.type === 'FRIEND_ACCEPTED') {
    const from = p.fromUser as { nickname?: string } | undefined
    const name = from?.nickname ?? '사용자'
    return item.type === 'FRIEND_REQUEST'
      ? `${name}님이 친구 요청을 보냈어요`
      : `${name}님이 친구 요청을 수락했어요`
  }
  const title = typeof p.title === 'string' ? p.title : '제안'
  if (item.type === 'PROPOSAL_RECEIVED') return `일정 제안이 도착했어요: ${title}`
  if (item.type === 'PROPOSAL_CONFIRMED') return `약속이 확정됐어요: ${title}`
  return `제안이 거절됐어요: ${title}`
}

function proposalIdOf(item: NotificationItem): number | null {
  const id = item.payload.proposalId
  return typeof id === 'number' ? id : null
}

export function NotificationBell() {
  const { data } = useNotifications(0, 20)
  const markRead = useMarkRead()
  const readAll = useReadAll()
  const respond = useRespondProposal()

  const [open, setOpen] = useState(false)
  const [declineTarget, setDeclineTarget] = useState<NotificationItem | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)

  const unreadCount = data?.unreadCount ?? 0
  const items = data?.content ?? []

  // 바깥 클릭·Esc로 드롭다운 닫기
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (rootRef.current !== null && !rootRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const onAccept = (item: NotificationItem) => {
    const id = proposalIdOf(item)
    if (id === null) return
    respond.mutate({ id, response: 'ACCEPTED' })
    markRead.mutate(item.id)
  }

  const onDeclineConfirm = () => {
    if (declineTarget === null) return
    const id = proposalIdOf(declineTarget)
    if (id !== null) respond.mutate({ id, response: 'DECLINED' })
    markRead.mutate(declineTarget.id)
    setDeclineTarget(null)
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label={`알림${unreadCount > 0 ? `, 읽지 않은 ${unreadCount}건` : ''}`}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="relative rounded-lg p-2 text-text-secondary transition duration-150 hover:bg-surface-muted"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" aria-hidden="true">
          <path
            d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        {/* 미읽음 표시는 dot 8px — 카운트 배지는 nav 항목 전용 (top-nav.md·시안 벨 실측) */}
        {unreadCount > 0 && (
          <span className="absolute right-0.5 top-0.5 size-2 rounded-full bg-danger-text ring-2 ring-surface" />
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 rounded-2xl border-2 border-border-strong bg-surface shadow-brutal-md">
          <div className="flex items-center justify-between border-b-2 border-border px-4 py-2.5">
            <p className="text-sm font-bold text-text">알림</p>
            <button
              type="button"
              onClick={() => readAll.mutate()}
              disabled={unreadCount === 0 || readAll.isPending}
              className="text-xs font-semibold text-text-secondary transition duration-150 hover:text-text disabled:opacity-40"
            >
              모두 읽기
            </button>
          </div>
          <ul className="max-h-80 overflow-y-auto">
            {items.length === 0 && (
              <li className="px-4 py-6 text-center text-sm text-text-secondary">
                알림이 없어요
              </li>
            )}
            {items.map((item) => (
              <li key={item.id} className="border-b border-border last:border-b-0">
                <button
                  type="button"
                  onClick={() => {
                    if (item.readAt === null) markRead.mutate(item.id)
                  }}
                  className={`flex w-full flex-col items-start gap-1 px-4 py-3 text-left transition duration-150 hover:bg-surface-muted ${
                    item.readAt === null ? 'bg-surface-muted/50' : ''
                  }`}
                >
                  <span className="flex w-full items-center gap-2">
                    {item.readAt === null && (
                      <span aria-hidden="true" className="h-1.5 w-1.5 shrink-0 rounded-full bg-accent-deep" />
                    )}
                    <span className="min-w-0 flex-1 truncate text-sm font-semibold text-text">
                      {readTitle(item)}
                    </span>
                  </span>
                  <span className="text-xs text-text-secondary">
                    {new Date(item.createdAt).toLocaleString('ko-KR')}
                  </span>
                </button>
                {item.type === 'PROPOSAL_RECEIVED' && proposalIdOf(item) !== null && (
                  <div className="flex gap-2 px-4 pb-3">
                    <button
                      type="button"
                      onClick={() => onAccept(item)}
                      disabled={respond.isPending}
                      className="rounded-lg border-2 border-border-strong bg-accent-deep px-2.5 py-1 text-xs font-bold text-white transition duration-150 hover:-translate-y-0.5 disabled:opacity-40"
                    >
                      수락
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeclineTarget(item)}
                      className="rounded-lg border-2 border-border-strong px-2.5 py-1 text-xs font-bold text-text-secondary transition duration-150 hover:bg-surface-muted"
                    >
                      거절
                    </button>
                  </div>
                )}
                {item.type.startsWith('PROPOSAL_') && (
                  <div className="px-4 pb-3">
                    <Link
                      to="/app/proposals"
                      onClick={() => setOpen(false)}
                      className="text-xs font-semibold text-accent-deep hover:underline"
                    >
                      제안 보관함에서 확인 →
                    </Link>
                  </div>
                )}
                {item.type === 'FRIEND_REQUEST' && (
                  <div className="px-4 pb-3">
                    <Link
                      to="/app/friends"
                      onClick={() => setOpen(false)}
                      className="text-xs font-semibold text-accent-deep hover:underline"
                    >
                      친구 탭에서 확인 →
                    </Link>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <ConfirmModal
        open={declineTarget !== null}
        onClose={() => setDeclineTarget(null)}
        onConfirm={onDeclineConfirm}
        title="제안 거절"
        description="이 일정 제안을 거절할까요? 주최자에게 거절 알림이 전달됩니다."
        confirmLabel="거절"
        tone="danger"
        pending={respond.isPending}
      />
    </div>
  )
}
