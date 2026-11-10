import { useMemo, useState } from 'react'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { Toast } from '../../../components/ui/toast'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
import {
  useCreateGroup,
  useDeleteGroup,
  useFriendGroups,
  useRenameGroup,
  useReplaceGroupMembers,
} from '../api/use-friend-groups'
import { useFriends } from '../api/use-friends'
import type { FriendGroup } from '../types'

const MAX_GROUPS = 3

function MemberAvatar({ nickname }: { nickname: string }) {
  return (
    <span
      aria-hidden="true"
      className="flex size-8 items-center justify-center rounded-full border-2 border-surface bg-primary text-xs font-bold text-on-tint"
    >
      {nickname.slice(0, 1)}
    </span>
  )
}

interface GroupFormProps {
  group: FriendGroup | null // null이면 생성 모드
  friends: { userId: number; nickname: string }[]
  onClose: () => void
  onAskDelete: (group: FriendGroup) => void
}

// 그룹 생성·편집 공용 폼 — 이름 + 멤버(친구만) 체크. 멤버는 저장 시 전량 교체.
function GroupFormModal({ group, friends, onClose, onAskDelete }: GroupFormProps) {
  const createGroup = useCreateGroup()
  const renameGroup = useRenameGroup()
  const replaceMembers = useReplaceGroupMembers()

  const [name, setName] = useState(group?.name ?? '')
  const [memberIds, setMemberIds] = useState<number[]>(group?.memberIds ?? [])
  const [error, setError] = useState<string | null>(null)

  const pending = createGroup.isPending || renameGroup.isPending || replaceMembers.isPending

  const toggleMember = (userId: number) =>
    setMemberIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId],
    )

  const fail = (e: unknown) =>
    setError(
      e instanceof ApiError && e.code === 'GROUP-LIMIT-EXCEEDED'
        ? '그룹은 최대 3개까지 만들 수 있어요.'
        : e instanceof ApiError && e.code === 'GROUP-MEMBER-INVALID'
          ? '친구만 그룹 멤버가 될 수 있어요.'
          : '저장에 실패했어요. 다시 시도해 주세요.',
    )

  const submit = () => {
    const trimmed = name.trim()
    if (trimmed === '') {
      setError('그룹 이름을 입력해 주세요.')
      return
    }
    if (group === null) {
      createGroup.mutate(
        { name: trimmed, memberIds },
        { onSuccess: onClose, onError: fail },
      )
      return
    }
    // 이름 변경과 멤버 교체는 별도 엔드포인트 — 바뀐 것만 보낸다
    const renameNeeded = trimmed !== group.name
    const membersChanged =
      memberIds.length !== group.memberIds.length ||
      memberIds.some((id) => !group.memberIds.includes(id))
    const done = { onSuccess: onClose, onError: fail }
    // 이름 저장 후 멤버 교체가 실패하면 부분 성공 상태다 — 어느 쪽이 실패했는지 구분해 알린다
    const membersDone = {
      onSuccess: onClose,
      onError: (e: unknown) => {
        if (e instanceof ApiError && e.code === 'GROUP-MEMBER-INVALID') {
          fail(e)
          return
        }
        setError('이름은 저장됐지만 멤버 변경에 실패했어요. 다시 저장해 주세요.')
      },
    }
    if (renameNeeded) {
      renameGroup.mutate({ groupId: group.groupId, name: trimmed }, {
        onSuccess: () => {
          if (membersChanged) {
            replaceMembers.mutate({ groupId: group.groupId, memberIds }, membersDone)
          } else {
            onClose()
          }
        },
        onError: fail,
      })
    } else if (membersChanged) {
      replaceMembers.mutate({ groupId: group.groupId, memberIds }, done)
    } else {
      onClose()
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={
        <h2 className="font-display text-lg">{group === null ? '새 그룹' : '그룹 편집'}</h2>
      }
    >
      <label className="block text-sm font-bold text-text">
        그룹 이름
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={30}
          placeholder="예: 스터디 모임"
          aria-label="그룹 이름"
          className="mt-1.5 w-full rounded-xl border-2 border-input bg-surface px-3 py-2.5 text-sm font-medium outline-none focus:border-accent"
        />
      </label>

      <fieldset className="mt-4">
        <legend className="text-sm font-bold text-text">멤버 (친구만 선택할 수 있어요)</legend>
        <ul className="mt-2 max-h-48 overflow-y-auto rounded-xl border border-border">
          {friends.length === 0 && (
            <li className="px-3 py-3 text-sm text-text-secondary">친구가 없어요</li>
          )}
          {friends.map((f) => (
            <li key={f.userId}>
              <label className="flex cursor-pointer items-center gap-2.5 px-3 py-2 text-sm text-text hover:bg-surface-muted">
                <input
                  type="checkbox"
                  checked={memberIds.includes(f.userId)}
                  onChange={() => toggleMember(f.userId)}
                  className="size-4 accent-accent"
                />
                {f.nickname}
              </label>
            </li>
          ))}
        </ul>
      </fieldset>

      {error !== null && (
        <p role="alert" className="mt-2 text-sm font-semibold text-danger-text">
          {error}
        </p>
      )}

      <div className="mt-4 flex items-center justify-between">
        {group !== null ? (
          <Button variant="danger" size="sm" onClick={() => onAskDelete(group)}>
            그룹 삭제
          </Button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button variant="primary" size="sm" onClick={submit} disabled={pending}>
            {pending ? '저장 중…' : '저장'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

// 시안 194:892 — "친구 그룹 (N/3)" + 그룹 카드(이름·멤버 수·아바타 스택) + dashed 생성 카드.
export function GroupsTab({ onToast }: { onToast: (message: string) => void }) {
  const groupsQuery = useFriendGroups()
  const friendsQuery = useFriends()
  const deleteGroup = useDeleteGroup()

  const [formTarget, setFormTarget] = useState<FriendGroup | 'new' | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<FriendGroup | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const groups = groupsQuery.data ?? []
  const remaining = MAX_GROUPS - groups.length

  // 멤버 아바타 라벨은 친구 목록의 닉네임을 재사용한다
  const nicknameOf = useMemo(() => {
    const map = new Map(
      (friendsQuery.data ?? []).map((f) => [f.user.id, f.user.nickname] as const),
    )
    return (userId: number) => map.get(userId) ?? '?'
  }, [friendsQuery.data])

  const friendOptions = (friendsQuery.data ?? []).map((f) => ({
    userId: f.user.id,
    nickname: f.user.nickname,
  }))

  return (
    <div className="mt-6">
      <p className="text-sm font-bold text-text">
        친구 그룹 ({groups.length}/{MAX_GROUPS})
      </p>

      {groupsQuery.isPending && <SkeletonRow rows={2} rowClassName="h-36" className="mt-4" />}
      <QueryErrorToast
        show={groupsQuery.isError}
        message="그룹을 불러오지 못했어요. 다시 시도해 주세요."
      />

      <ul className="mt-4 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
        {groups.map((g) => (
          <li key={g.groupId}>
            <button
              type="button"
              onClick={() => setFormTarget(g)}
              className="flex w-full flex-col gap-2 rounded-lg border border-border bg-surface p-5 text-left shadow-card-sm transition duration-150 hover:bg-surface-muted/60"
            >
              <span className="text-sm font-bold text-text">{g.name}</span>
              <span className="text-xs text-text-secondary">멤버 {g.memberIds.length}명</span>
              <span className="mt-1 flex -space-x-2">
                {g.memberIds.slice(0, 5).map((id) => (
                  <MemberAvatar key={id} nickname={nicknameOf(id)} />
                ))}
              </span>
            </button>
          </li>
        ))}
        <li>
          <button
            type="button"
            onClick={() => remaining > 0 && setFormTarget('new')}
            disabled={remaining <= 0}
            aria-disabled={remaining <= 0}
            className={cx(
              'flex h-full w-full flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed border-border-strong p-5 text-center transition duration-150',
              remaining > 0
                ? 'text-text-secondary hover:bg-surface-muted/60'
                : 'cursor-not-allowed text-text-muted opacity-60',
            )}
          >
            <span aria-hidden="true" className="text-2xl">
              +
            </span>
            <span className="text-sm font-bold">새 그룹 만들기</span>
            <span className="text-xs">
              {remaining > 0
                ? `${remaining}개 더 만들 수 있어요`
                : '최대 3개까지 만들 수 있어요'}
            </span>
          </button>
        </li>
      </ul>

      {formTarget !== null && (
        <GroupFormModal
          group={formTarget === 'new' ? null : formTarget}
          friends={friendOptions}
          onClose={() => setFormTarget(null)}
          onAskDelete={(g) => {
            setFormTarget(null)
            setDeleteTarget(g)
          }}
        />
      )}

      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget === null) return
          deleteGroup.mutate(deleteTarget.groupId, {
            onSuccess: () => {
              setDeleteTarget(null)
              onToast('그룹을 삭제했어요.')
            },
            onError: () => {
              setDeleteTarget(null)
              setToast('삭제에 실패했어요. 다시 시도해 주세요.')
            },
          })
        }}
        title="그룹 삭제"
        description={
          deleteTarget !== null
            ? `'${deleteTarget.name}' 그룹을 삭제할까요? 이 그룹으로 공개된 항목은 비공개로 바뀝니다.`
            : undefined
        }
        confirmLabel="삭제"
        tone="danger"
        pending={deleteGroup.isPending}
      />
      {toast !== null && (
        <Toast variant="danger" message={toast} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
