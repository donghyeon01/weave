import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useFriendGroups } from '../../features/friends/api/use-friend-groups'
import { useMyShares, useUpsertShare } from '../../features/share/api/use-share'
import type { ShareItemType, ShareVisibility } from '../../features/share/types'
import { ApiError } from '../../lib/api-client'
import { cx } from '../../utils/cx'
import { Button } from '../ui/button'
import { Modal } from '../ui/modal'

// 여러 feature(diary·events·share)가 여는 공개 범위 모달이라 components/share에 둔다
// (feature 간 직접 import 금지 — components→features는 허용).

const OPTIONS: { value: Exclude<ShareVisibility, never>; label: string; desc: string }[] = [
  { value: 'PRIVATE', label: '비공개', desc: '나만 볼 수 있어요' },
  { value: 'FRIENDS', label: '전체 친구', desc: '모든 친구가 피드에서 볼 수 있어요' },
  { value: 'GROUP', label: '그룹', desc: '선택한 그룹의 멤버만 볼 수 있어요' },
  {
    value: 'PUBLIC',
    label: '전체 공개',
    desc: 'Weave 사용자 모두 — 발견 피드는 추후 제공',
  },
]

interface VisibilityModalProps {
  open: boolean
  onClose: () => void
  itemType: ShareItemType
  itemId: number
}

// 시안 195:1683 — 4단계 라디오 + 그룹 선택 칩 + 취소/저장.
export function VisibilityModal({ open, onClose, itemType, itemId }: VisibilityModalProps) {
  const navigate = useNavigate()
  const mySharesQuery = useMyShares()
  const groupsQuery = useFriendGroups()
  const upsert = useUpsertShare()

  const [visibility, setVisibility] = useState<ShareVisibility>('PRIVATE')
  const [groupId, setGroupId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 현재 설정 반영 — 행 없음·PRIVATE은 모두 "비공개"로 열린다 (계약상 동치).
  const current = useMemo(
    () =>
      (mySharesQuery.data ?? []).find(
        (s) => s.itemType === itemType && s.itemId === itemId,
      ) ?? null,
    [mySharesQuery.data, itemType, itemId],
  )

  useEffect(() => {
    if (!open) return
    setVisibility(current?.visibility ?? 'PRIVATE')
    setGroupId(current?.groupId ?? null)
    setError(null)
  }, [open, current])

  const groups = groupsQuery.data ?? []
  const canSave = visibility !== 'GROUP' || groupId !== null

  const save = () => {
    upsert.mutate(
      {
        itemType,
        itemId,
        visibility,
        groupId: visibility === 'GROUP' ? (groupId ?? undefined) : undefined,
      },
      {
        onSuccess: onClose,
        onError: (e) =>
          setError(
            e instanceof ApiError && e.code === 'VISIBILITY-INVALID'
              ? '그룹을 선택해 주세요.'
              : '저장에 실패했어요. 다시 시도해 주세요.',
          ),
      },
    )
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={
        <div>
          <h2 className="font-display text-lg">공개 범위</h2>
          <p className="mt-1 text-sm font-medium text-text-secondary">
            이 항목을 누가 볼 수 있나요?
          </p>
        </div>
      }
    >
      <div role="radiogroup" aria-label="공개 범위" className="flex flex-col gap-3">
        {OPTIONS.map((opt) => {
          const active = visibility === opt.value
          return (
            <div key={opt.value}>
              <button
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setVisibility(opt.value)}
                className={cx(
                  'flex w-full items-start gap-3 rounded-xl border-2 px-4 py-3.5 text-left transition duration-150',
                  active
                    ? 'border-accent bg-accent-soft/40'
                    : 'border-border bg-surface hover:border-accent/60',
                )}
              >
                <span
                  aria-hidden="true"
                  className={cx(
                    'mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full border-2',
                    active ? 'border-accent-deep' : 'border-border-strong',
                  )}
                >
                  {active && <span className="size-2.5 rounded-full bg-accent-deep" />}
                </span>
                <span>
                  <span className="block text-sm font-bold text-text">{opt.label}</span>
                  <span className="mt-0.5 block text-xs text-text-secondary">{opt.desc}</span>
                </span>
              </button>
              {opt.value === 'GROUP' && active && (
                <div className="mt-2 flex flex-wrap items-center gap-2 pl-8">
                  {groups.map((g) => (
                    <button
                      key={g.groupId}
                      type="button"
                      aria-pressed={groupId === g.groupId}
                      onClick={() => setGroupId(g.groupId)}
                      className={cx(
                        'rounded-full px-3 py-1.5 text-xs font-bold transition duration-150',
                        groupId === g.groupId
                          ? 'bg-accent-soft text-on-tint'
                          : 'bg-surface-muted text-text-secondary hover:text-text',
                      )}
                    >
                      {g.name} ({g.memberIds.length}명)
                    </button>
                  ))}
                  {groupsQuery.isError && (
                    <span className="text-xs font-semibold text-danger-text">
                      그룹을 불러오지 못했어요. 다시 열어 주세요.
                    </span>
                  )}
                  {!groupsQuery.isError && groups.length === 0 && (
                    <span className="text-xs text-text-secondary">아직 그룹이 없어요</span>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      onClose()
                      navigate('/app/friends?tab=groups')
                    }}
                    className="text-xs font-bold text-accent-text hover:underline"
                  >
                    그룹 관리
                  </button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {error !== null && (
        <p role="alert" className="mt-3 text-sm font-semibold text-danger-text">
          {error}
        </p>
      )}

      <div className="mt-5 flex justify-center gap-2">
        <Button variant="ghost" size="sm" onClick={onClose}>
          취소
        </Button>
        <Button variant="primary" size="sm" onClick={save} disabled={!canSave || upsert.isPending}>
          {upsert.isPending ? '저장 중…' : '저장'}
        </Button>
      </div>
    </Modal>
  )
}
