import { useMemo, useState } from 'react'
import type { ProposalStatus, ProposalSummary, Slot } from '../types'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { cx } from '../../../utils/cx'
import { useProposal, useProposals } from '../api/use-proposals'
import { useCancelProposal, useRespondProposal } from '../api/use-proposal-mutations'
import { EmptyState } from '../../../components/ui/empty-state'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'

type Box = 'received' | 'sent'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

const STATUS_META: Record<ProposalStatus, { label: string; variant: 'default' | 'success' | 'danger' }> = {
  PENDING: { label: '대기 중', variant: 'default' },
  CONFIRMED: { label: '확정', variant: 'success' },
  DECLINED: { label: '거절됨', variant: 'danger' },
  CANCELLED: { label: '취소됨', variant: 'default' },
}

// "5월 18일 (일) 12:30–14:00" — 시안 보관함 행 표기
function formatSlotHuman(slot: Slot): string {
  if (slot.type === 'DATE') return `${slot.startDate} ~ ${slot.endDate}`
  const d = new Date(`${slot.date}T00:00:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]}) ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
}

// Figma 138:302(받은)·139:351(보낸) — 제안 보관함. 상태 배지 + 수락 현황 칩 + 행 단위 액션.
export function ProposalsPage() {
  const respond = useRespondProposal()
  const cancel = useCancelProposal()

  const [box, setBox] = useState<Box>('received')
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [confirmAction, setConfirmAction] = useState<
    { kind: 'decline'; id: number } | { kind: 'cancel'; id: number } | null
  >(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const proposalsQuery = useProposals(box, page)
  const receivedQuery = useProposals('received', 0, 1)
  const sentQuery = useProposals('sent', 0, 1)
  const detailQuery = useProposal(detailId)

  const items = useMemo(() => proposalsQuery.data?.content ?? [], [proposalsQuery.data])
  const pendingCount = useMemo(
    () => items.filter((p) => p.status === 'PENDING').length,
    [items],
  )

  const onConfirmAction = () => {
    if (confirmAction === null) return
    if (confirmAction.kind === 'decline') {
      respond.mutate(
        { id: confirmAction.id, response: 'DECLINED' },
        {
          onSuccess: () => {
            setConfirmAction(null)
            setToast({ variant: 'success', message: '제안을 거절했어요.' })
          },
          onError: () => setToast({ variant: 'danger', message: '거절 처리에 실패했어요.' }),
        },
      )
    } else {
      cancel.mutate(confirmAction.id, {
        onSuccess: () => {
          setConfirmAction(null)
          setToast({ variant: 'success', message: '제안을 취소했어요.' })
        },
        onError: () => setToast({ variant: 'danger', message: '취소에 실패했어요.' }),
      })
    }
  }

  const accept = (p: ProposalSummary) => {
    respond.mutate(
      { id: p.id, response: 'ACCEPTED' },
      {
        onSuccess: () => setToast({ variant: 'success', message: '제안을 수락했어요.' }),
        onError: () => setToast({ variant: 'danger', message: '수락에 실패했어요.' }),
      },
    )
  }

  return (
    <div className="mx-auto w-full max-w-[1024px] p-8">
      <div className="flex flex-col gap-2.5">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-[32px] leading-normal">제안 보관함</h1>
          {pendingCount > 0 && (
            <Badge variant="accent">
              {box === 'received' ? `미응답 ${pendingCount}건` : `응답 대기 ${pendingCount}건`}
            </Badge>
          )}
        </div>
        <p className="text-base font-medium text-text-secondary">
          받은 제안에 응답하고 보낸 제안의 수락 현황을 확인하세요
        </p>
      </div>

      <div
        role="tablist"
        aria-label="제안 보관함 탭"
        className="mt-6 inline-flex items-center gap-1 rounded-lg border border-border bg-background p-1.5"
      >
        {(
          [
            { key: 'received', label: '받은 제안', count: receivedQuery.data?.totalElements ?? 0 },
            { key: 'sent', label: '보낸 제안', count: sentQuery.data?.totalElements ?? 0 },
          ] as const
        ).map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={box === t.key}
            onClick={() => {
              setBox(t.key)
              setPage(0)
            }}
            className={cx(
              'rounded-md px-4 py-1.5 text-sm font-bold transition duration-150',
              box === t.key
                ? 'border border-border bg-surface text-accent-text shadow-sm'
                : 'text-text-secondary hover:text-text',
            )}
          >
            {t.label}{' '}
            <span className={box === t.key ? 'text-accent-text' : 'text-text-muted'}>
              ({t.count})
            </span>
          </button>
        ))}
      </div>

      {proposalsQuery.isPending && <SkeletonRow rows={3} className="mt-6" />}
      <QueryErrorToast
        show={proposalsQuery.isError}
        message="제안을 불러오지 못했어요. 다시 시도해 주세요."
      />

      {items.length > 0 && (
        <ul className="mt-6 flex flex-col gap-3">
          {items.map((p) => {
            const meta = STATUS_META[p.status]
            return (
              <li
                key={p.id}
                className="flex items-center justify-between rounded-xl border border-border bg-surface p-4 shadow-[0_2px_10px_rgba(0,0,0,0.04)]"
              >
                <div className="flex min-w-0 flex-col gap-1.5">
                  <div className="flex items-center gap-2">
                    <p className="truncate text-base font-bold text-text">{p.title}</p>
                    <Badge variant={meta.variant}>{meta.label}</Badge>
                  </div>
                  <p className="text-sm font-medium text-text-secondary">
                    {formatSlotHuman(p.slot)}
                    {box === 'received' && ` · ${p.organizer.nickname} 님의 제안`}
                  </p>
                  <div className="flex items-center gap-1.5">
                    <Badge>
                      수락 {p.acceptCount}/{p.totalCount}
                    </Badge>
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {box === 'received' && p.status === 'PENDING' && (
                    <>
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => accept(p)}
                        disabled={respond.isPending}
                      >
                        <svg viewBox="0 0 14 14" className="h-3.5 w-3.5" aria-hidden="true">
                          <path
                            fill="currentColor"
                            d="m5.4 10.7-2.8-2.8 1.1-1.1 1.7 1.6 4.9-4.9 1.1 1.2-6 6Z"
                          />
                        </svg>
                        수락
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setConfirmAction({ kind: 'decline', id: p.id })}
                      >
                        거절
                      </Button>
                    </>
                  )}
                  {box === 'sent' && p.status === 'PENDING' && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setConfirmAction({ kind: 'cancel', id: p.id })}
                    >
                      제안 취소
                    </Button>
                  )}
                  {p.status !== 'PENDING' && (
                    <span className="text-[13px] font-medium text-text-muted">
                      {p.status === 'CONFIRMED'
                        ? '확정된 약속'
                        : p.status === 'DECLINED'
                          ? '거절된 제안'
                          : '취소한 제안'}
                    </span>
                  )}
                  <Button variant="ghost" size="sm" onClick={() => setDetailId(p.id)}>
                    상세
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {proposalsQuery.data !== undefined && items.length === 0 && (
        <EmptyState
          className="mt-6"
          title={box === 'received' ? '받은 제안이 없어요' : '보낸 제안이 없어요'}
          description={
            box === 'received'
              ? '친구가 보낸 일정 제안이 여기에 도착합니다'
              : '일정 조율에서 제안을 보내면 여기에 표시됩니다'
          }
        />
      )}

      {proposalsQuery.data !== undefined && proposalsQuery.data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            이전
          </Button>
          <span className="text-sm font-semibold text-text-secondary">
            {page + 1} / {proposalsQuery.data.totalPages}
          </span>
          <Button
            variant="ghost"
            size="sm"
            disabled={page + 1 >= proposalsQuery.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}

      {/* 제안 상세 — 참여자별 응답 현황 (시안 참여자 칩은 상세 모달로 대체) */}
      <Modal
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        title={
          <h2 className="font-display text-lg">{detailQuery.data?.title ?? '제안 상세'}</h2>
        }
      >
        {detailQuery.data !== undefined && (
          <div>
            <p className="text-sm text-text-secondary">
              {formatSlotHuman(detailQuery.data.slot)} · 주최 {detailQuery.data.organizer.nickname} ·{' '}
              {STATUS_META[detailQuery.data.status].label}
            </p>
            <ul className="mt-3 space-y-1">
              {detailQuery.data.participants.map((p) => (
                <li
                  key={p.user.id}
                  className="flex items-center justify-between rounded-lg bg-surface-muted px-3 py-2 text-sm"
                >
                  <span className="font-semibold text-text">{p.user.nickname}</span>
                  <Badge
                    variant={
                      p.response === 'ACCEPTED'
                        ? 'success'
                        : p.response === 'DECLINED'
                          ? 'danger'
                          : 'default'
                    }
                  >
                    {p.response === 'PENDING'
                      ? '대기'
                      : p.response === 'ACCEPTED'
                        ? '수락'
                        : '거절'}
                  </Badge>
                </li>
              ))}
            </ul>
            {detailQuery.data.status === 'PENDING' && (
              <div className="mt-4 flex justify-end gap-2">
                {box === 'received' ? (
                  <>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setDetailId(null)
                        setConfirmAction({ kind: 'decline', id: detailQuery.data.id })
                      }}
                    >
                      거절
                    </Button>
                    <Button
                      variant="primary"
                      size="sm"
                      disabled={respond.isPending}
                      onClick={() =>
                        respond.mutate(
                          { id: detailQuery.data.id, response: 'ACCEPTED' },
                          {
                            onSuccess: () => {
                              setDetailId(null)
                              setToast({ variant: 'success', message: '제안을 수락했어요.' })
                            },
                          },
                        )
                      }
                    >
                      수락
                    </Button>
                  </>
                ) : (
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => {
                      setDetailId(null)
                      setConfirmAction({ kind: 'cancel', id: detailQuery.data.id })
                    }}
                  >
                    제안 취소
                  </Button>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>

      <ConfirmModal
        open={confirmAction !== null}
        onClose={() => setConfirmAction(null)}
        onConfirm={onConfirmAction}
        title={confirmAction?.kind === 'decline' ? '제안 거절' : '제안 취소'}
        description={
          confirmAction?.kind === 'decline'
            ? '이 일정 제안을 거절할까요? 주최자에게 알림이 전달됩니다.'
            : '제안을 취소할까요? 참여자에게는 알림이 가지 않습니다.'
        }
        confirmLabel={confirmAction?.kind === 'decline' ? '거절' : '취소'}
        tone="danger"
        pending={respond.isPending || cancel.isPending}
      />
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
