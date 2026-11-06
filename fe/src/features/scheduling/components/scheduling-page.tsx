import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { Candidate, ProposalSummary, Slot } from '../types'
import { formatSlot } from '../types'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { useFriends } from '../../friends/api/use-friends'
import { useRecommend } from '../api/use-recommend'
import { useProposal, useProposals } from '../api/use-proposals'
import {
  useCancelProposal,
  useCreateProposal,
  useRespondProposal,
} from '../api/use-proposal-mutations'

const STATUS_LABEL: Record<ProposalSummary['status'], string> = {
  PENDING: '대기',
  CONFIRMED: '확정',
  DECLINED: '거절됨',
  CANCELLED: '취소됨',
}

function todayPlus(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

export function SchedulingPage() {
  const friendsQuery = useFriends()
  const recommend = useRecommend()
  const createProposal = useCreateProposal()
  const respond = useRespondProposal()
  const cancel = useCancelProposal()

  // 조건 폼 상태
  const [mode, setMode] = useState<'TIME' | 'DATE'>('TIME')
  const [rangeStart, setRangeStart] = useState(todayPlus(0))
  const [rangeEnd, setRangeEnd] = useState(todayPlus(7))
  const [windowStart, setWindowStart] = useState('09:00')
  const [windowEnd, setWindowEnd] = useState('22:00')
  const [slotMinutes, setSlotMinutes] = useState('')
  const [durationDays, setDurationDays] = useState('2')
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  const [candidates, setCandidates] = useState<Candidate[] | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [proposalSlot, setProposalSlot] = useState<Slot | null>(null)
  const [proposalTitle, setProposalTitle] = useState('')
  const [box, setBox] = useState<'received' | 'sent'>('received')
  const [boxPage, setBoxPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [confirmAction, setConfirmAction] = useState<
    { kind: 'decline'; id: number } | { kind: 'cancel'; id: number } | null
  >(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const friends = useMemo(() => friendsQuery.data ?? [], [friendsQuery.data])
  const proposalsQuery = useProposals(box, boxPage)
  const detailQuery = useProposal(detailId)

  const toggleParticipant = (id: number) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    )
  }

  const runRecommend = (e: FormEvent) => {
    e.preventDefault()
    setFormError(null)
    recommend.mutate(
      {
        mode,
        rangeStart,
        rangeEnd,
        participantIds: selectedIds.length > 0 ? selectedIds : undefined,
        windowStart: mode === 'TIME' ? windowStart : undefined,
        windowEnd: mode === 'TIME' ? windowEnd : undefined,
        slotMinutes: mode === 'TIME' && slotMinutes !== '' ? Number(slotMinutes) : undefined,
        durationDays: mode === 'DATE' ? Number(durationDays) : undefined,
      },
      {
        onSuccess: (res) => setCandidates(res.candidates),
        onError: (err) =>
          setFormError(
            err instanceof ApiError && err.code === 'SCHEDULING-NOT-FRIEND'
              ? '선택한 참여자 중 친구가 아닌 사람이 있어요.'
              : '추천 계산에 실패했어요. 조건을 확인해 주세요.',
          ),
      },
    )
  }

  const submitProposal = (e: FormEvent) => {
    e.preventDefault()
    if (proposalSlot === null || proposalTitle.trim() === '') return
    createProposal.mutate(
      { title: proposalTitle.trim(), slot: proposalSlot, participantIds: selectedIds },
      {
        onSuccess: () => {
          setProposalSlot(null)
          setProposalTitle('')
          setToast({ variant: 'success', message: '제안을 보냈어요.' })
        },
        onError: () =>
          setToast({ variant: 'danger', message: '제안 전송에 실패했어요.' }),
      },
    )
  }

  const onConfirmAction = () => {
    if (confirmAction === null) return
    if (confirmAction.kind === 'decline') {
      respond.mutate(
        { id: confirmAction.id, response: 'DECLINED' },
        {
          onSuccess: () => setConfirmAction(null),
          onError: () =>
            setToast({ variant: 'danger', message: '거절 처리에 실패했어요.' }),
        },
      )
    } else {
      cancel.mutate(confirmAction.id, {
        onSuccess: () => setConfirmAction(null),
        onError: () => setToast({ variant: 'danger', message: '취소에 실패했어요.' }),
      })
    }
  }

  const inputCls =
    'w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm outline-none focus:outline-ring'

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="font-display text-2xl">일정 조율</h1>

      <div className="mt-5 grid gap-6 lg:grid-cols-[320px_1fr]">
        {/* 좌측 — 조건 폼 */}
        <form
          onSubmit={runRecommend}
          className="rounded-2xl border-2 border-border-strong bg-surface p-5 shadow-brutal-sm"
        >
          <div role="tablist" aria-label="조율 모드" className="flex gap-1 rounded-xl border-2 border-border-strong p-1">
            {(['TIME', 'DATE'] as const).map((m) => (
              <button
                key={m}
                type="button"
                role="tab"
                aria-selected={mode === m}
                onClick={() => setMode(m)}
                className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-semibold ${
                  mode === m ? 'bg-accent-deep text-white' : 'text-text-secondary'
                }`}
              >
                {m === 'TIME' ? '시간 약속' : '날짜 약속'}
              </button>
            ))}
          </div>

          <div className="mt-4 grid grid-cols-2 gap-2">
            <label className="text-xs font-semibold text-text-secondary">
              시작일
              <input
                type="date"
                value={rangeStart}
                onChange={(e) => setRangeStart(e.target.value)}
                className={`mt-1 ${inputCls}`}
              />
            </label>
            <label className="text-xs font-semibold text-text-secondary">
              종료일
              <input
                type="date"
                value={rangeEnd}
                onChange={(e) => setRangeEnd(e.target.value)}
                className={`mt-1 ${inputCls}`}
              />
            </label>
          </div>

          {mode === 'TIME' ? (
            <>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <label className="text-xs font-semibold text-text-secondary">
                  시간창 시작
                  <input
                    type="time"
                    value={windowStart}
                    onChange={(e) => setWindowStart(e.target.value)}
                    className={`mt-1 ${inputCls}`}
                  />
                </label>
                <label className="text-xs font-semibold text-text-secondary">
                  시간창 끝
                  <input
                    type="time"
                    value={windowEnd}
                    onChange={(e) => setWindowEnd(e.target.value)}
                    className={`mt-1 ${inputCls}`}
                  />
                </label>
              </div>
              <label className="mt-3 block text-xs font-semibold text-text-secondary">
                슬롯 길이(분, 비우면 고정 4블록)
                <input
                  type="number"
                  min={15}
                  step={15}
                  value={slotMinutes}
                  onChange={(e) => setSlotMinutes(e.target.value)}
                  placeholder="60"
                  className={`mt-1 ${inputCls}`}
                />
              </label>
            </>
          ) : (
            <label className="mt-3 block text-xs font-semibold text-text-secondary">
              연속 기간(일)
              <input
                type="number"
                min={1}
                value={durationDays}
                onChange={(e) => setDurationDays(e.target.value)}
                className={`mt-1 ${inputCls}`}
              />
            </label>
          )}

          <fieldset className="mt-4">
            <legend className="text-xs font-semibold text-text-secondary">
              참여자 (비우면 친구 전원)
            </legend>
            <ul className="mt-1 space-y-1">
              {friends.map((f) => (
                <li key={f.friendshipId}>
                  <label className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-surface-muted">
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(f.user.id)}
                      onChange={() => toggleParticipant(f.user.id)}
                      className="h-4 w-4 accent-accent-deep"
                    />
                    {f.user.nickname}
                  </label>
                </li>
              ))}
              {friends.length === 0 && (
                <li className="px-2 py-1.5 text-sm text-text-secondary">
                  친구가 없어요 — 친구 탭에서 먼저 추가하세요.
                </li>
              )}
            </ul>
          </fieldset>

          {formError !== null && (
            <p role="alert" className="mt-3 text-sm font-semibold text-danger-text">
              {formError}
            </p>
          )}
          <Button variant="primary" type="submit" className="mt-4 w-full" disabled={recommend.isPending}>
            {recommend.isPending ? '계산 중…' : '추천 시간 계산'}
          </Button>
        </form>

        {/* 우측 — 결과 + 제안 목록 */}
        <div>
          {candidates !== null && (
            <section aria-label="추천 결과" className="mb-6">
              <h2 className="text-sm font-bold text-text">추천 후보</h2>
              {candidates.length === 0 && (
                <div className="mt-2 rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-8 text-center text-sm font-semibold text-text-secondary">
                  모두 가능한 시간이 없어요. 기간이나 참여자를 조정해 보세요.
                </div>
              )}
              <ol className="mt-2 space-y-2">
                {candidates.map((c) => (
                  <li
                    key={`${c.rank}-${formatSlot(c.slot)}`}
                    className={`flex items-center gap-3 rounded-2xl border-2 border-border-strong px-4 py-3 ${
                      c.rank === 1
                        ? 'bg-accent-deep text-white shadow-brutal-md'
                        : 'bg-surface shadow-brutal-sm'
                    }`}
                  >
                    <span className={`font-display text-lg ${c.rank === 1 ? 'text-white' : 'text-accent-deep'}`}>
                      {c.rank}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold">{formatSlot(c.slot)}</p>
                      <p className={`text-xs ${c.rank === 1 ? 'text-white/80' : 'text-text-secondary'}`}>
                        {c.totalCount}명 중 {c.availableCount}명 가능 ({c.percent}%)
                      </p>
                    </div>
                    {selectedIds.length > 0 || friends.length > 0 ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setProposalSlot(c.slot)}
                      >
                        제안
                      </Button>
                    ) : null}
                  </li>
                ))}
              </ol>
            </section>
          )}

          <section aria-label="제안 목록">
            <div role="tablist" aria-label="제안 보관함" className="flex gap-1 rounded-xl border-2 border-border-strong bg-surface p-1">
              {(['received', 'sent'] as const).map((b) => (
                <button
                  key={b}
                  type="button"
                  role="tab"
                  aria-selected={box === b}
                  onClick={() => {
                    setBox(b)
                    setBoxPage(0)
                  }}
                  className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-semibold ${
                    box === b ? 'bg-accent-deep text-white' : 'text-text-secondary'
                  }`}
                >
                  {b === 'received' ? '받은 제안' : '보낸 제안'}
                </button>
              ))}
            </div>
            <ul className="mt-2 space-y-2">
              {(proposalsQuery.data?.content ?? []).map((p) => (
                <li
                  key={p.id}
                  className="flex items-center gap-3 rounded-2xl border-2 border-border-strong bg-surface px-4 py-3 shadow-brutal-sm"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold text-text">{p.title}</p>
                    <p className="text-xs text-text-secondary">
                      {formatSlot(p.slot)} · {p.organizer.nickname} · {p.totalCount}명 중 {p.acceptCount}명 수락
                    </p>
                  </div>
                  <span
                    className={`rounded-lg px-2 py-0.5 text-xs font-bold ${
                      p.status === 'CONFIRMED'
                        ? 'bg-accent-deep text-white'
                        : p.status === 'PENDING'
                          ? 'bg-surface-muted text-text-secondary'
                          : 'bg-danger text-danger-text'
                    }`}
                  >
                    {STATUS_LABEL[p.status]}
                  </span>
                  <Button variant="ghost" size="sm" onClick={() => setDetailId(p.id)}>
                    상세
                  </Button>
                </li>
              ))}
              {proposalsQuery.data !== undefined && proposalsQuery.data.content.length === 0 && (
                <li className="rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-8 text-center text-sm font-semibold text-text-secondary">
                  {box === 'received' ? '받은 제안이 없어요.' : '보낸 제안이 없어요.'}
                </li>
              )}
            </ul>
            {proposalsQuery.data !== undefined && proposalsQuery.data.totalPages > 1 && (
              <div className="mt-3 flex items-center justify-center gap-3">
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={boxPage === 0}
                  onClick={() => setBoxPage((p) => p - 1)}
                >
                  이전
                </Button>
                <span className="text-sm font-semibold text-text-secondary">
                  {boxPage + 1} / {proposalsQuery.data.totalPages}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={boxPage + 1 >= proposalsQuery.data.totalPages}
                  onClick={() => setBoxPage((p) => p + 1)}
                >
                  다음
                </Button>
              </div>
            )}
          </section>
        </div>
      </div>

      {/* 제안 생성 모달 */}
      <Modal
        open={proposalSlot !== null}
        onClose={() => setProposalSlot(null)}
        title={
          <h2 className="font-display text-lg">
            제안 보내기 — {proposalSlot !== null && formatSlot(proposalSlot)}
          </h2>
        }
      >
        <form onSubmit={submitProposal}>
          <label className="block text-sm font-semibold text-text">
            제목
            <input
              type="text"
              value={proposalTitle}
              onChange={(e) => setProposalTitle(e.target.value)}
              placeholder="예: 캡스톤 회의"
              className={`mt-1 ${inputCls}`}
            />
          </label>
          <p className="mt-2 text-xs text-text-secondary">
            참여자: {selectedIds.length > 0 ? `${selectedIds.length}명` : '친구 전원'}
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" type="button" onClick={() => setProposalSlot(null)}>
              취소
            </Button>
            <Button variant="primary" type="submit" disabled={proposalTitle.trim() === '' || createProposal.isPending}>
              보내기
            </Button>
          </div>
        </form>
      </Modal>

      {/* 제안 상세 + 응답 현황 모달 */}
      <Modal
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        title={
          <h2 className="font-display text-lg">
            {detailQuery.data?.title ?? '제안 상세'}
          </h2>
        }
      >
        {detailQuery.data !== undefined && (
          <div>
            <p className="text-sm text-text-secondary">
              {formatSlot(detailQuery.data.slot)} · 주최 {detailQuery.data.organizer.nickname} ·{' '}
              {STATUS_LABEL[detailQuery.data.status]}
            </p>
            <ul className="mt-3 space-y-1">
              {detailQuery.data.participants.map((p) => (
                <li
                  key={p.user.id}
                  className="flex items-center justify-between rounded-lg bg-surface-muted px-3 py-2 text-sm"
                >
                  <span className="font-semibold text-text">{p.user.nickname}</span>
                  <span className="text-xs font-bold text-text-secondary">
                    {p.response === 'PENDING' ? '대기' : p.response === 'ACCEPTED' ? '수락' : '거절'}
                  </span>
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
                      onClick={() => setConfirmAction({ kind: 'decline', id: detailQuery.data.id })}
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
                          { onSuccess: () => setDetailId(null) },
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
                    onClick={() => setConfirmAction({ kind: 'cancel', id: detailQuery.data.id })}
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
