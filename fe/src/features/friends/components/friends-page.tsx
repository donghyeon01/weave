import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { FriendshipItem } from '../types'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import {
  useAcceptRequest,
  useDeleteFriendship,
  useSendRequest,
} from '../api/use-friend-mutations'
import {
  useFriends,
  useReceivedRequests,
  useSentRequests,
} from '../api/use-friends'

type Tab = 'all' | 'received' | 'sent'

const TABS: { key: Tab; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'received', label: '받은 요청' },
  { key: 'sent', label: '보낸 요청' },
]

type PendingAction =
  | { kind: 'reject'; item: FriendshipItem }
  | { kind: 'cancel'; item: FriendshipItem }
  | { kind: 'delete'; item: FriendshipItem }

const ACTION_TEXT: Record<PendingAction['kind'], { title: string; confirm: string; desc: string }> = {
  reject: { title: '요청 거절', confirm: '거절', desc: '받은 친구 요청을 거절할까요?' },
  cancel: { title: '요청 취소', confirm: '취소', desc: '보낸 친구 요청을 취소할까요?' },
  delete: { title: '친구 삭제', confirm: '삭제', desc: '친구 목록에서 삭제할까요?' },
}

function Avatar({ user }: { user: FriendshipItem['user'] }) {
  if (user.profileImage !== null) {
    return (
      <img
        src={user.profileImage}
        alt=""
        className="h-10 w-10 rounded-full border-2 border-border-strong object-cover"
      />
    )
  }
  return (
    <span
      aria-hidden="true"
      className="flex h-10 w-10 items-center justify-center rounded-full border-2 border-border-strong bg-accent-deep font-display text-white"
    >
      {user.nickname.slice(0, 1)}
    </span>
  )
}

export function FriendsPage() {
  const friendsQuery = useFriends()
  const receivedQuery = useReceivedRequests()
  const sentQuery = useSentRequests()
  const sendRequest = useSendRequest()
  const acceptRequest = useAcceptRequest()
  const deleteFriendship = useDeleteFriendship()

  const [tab, setTab] = useState<Tab>('all')
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)
  const [toast, setToast] = useState<{ variant: 'success' | 'danger'; message: string } | null>(null)

  const friends = useMemo(() => friendsQuery.data ?? [], [friendsQuery.data])
  const received = useMemo(() => receivedQuery.data ?? [], [receivedQuery.data])
  const sent = useMemo(() => sentQuery.data ?? [], [sentQuery.data])
  const filteredFriends = useMemo(
    () =>
      search === ''
        ? friends
        : friends.filter(
            (f) => f.user.nickname.includes(search) || f.user.email.includes(search),
          ),
    [friends, search],
  )

  const submitRequest = (e: FormEvent) => {
    e.preventDefault()
    if (email.trim() === '') {
      setEmailError('이메일을 입력해 주세요.')
      return
    }
    sendRequest.mutate(email.trim(), {
      onSuccess: () => {
        setEmail('')
        setEmailError(null)
        setToast({ variant: 'success', message: '친구 요청을 보냈어요.' })
      },
      onError: (err) => {
        if (err instanceof ApiError && err.code === 'USER-NOT-FOUND') {
          setEmailError('해당 이메일의 사용자가 없습니다.')
          return
        }
        const message =
          err instanceof ApiError && err.code === 'FRIEND-ALREADY'
            ? '이미 친구인 사용자입니다.'
            : err instanceof ApiError && err.code === 'FRIEND-DUPLICATE-REQUEST'
              ? '이미 친구 요청을 보냈습니다.'
              : err instanceof ApiError && err.code === 'FRIEND-SELF-REQUEST'
                ? '본인에게는 요청을 보낼 수 없습니다.'
                : '요청에 실패했어요. 다시 시도해 주세요.'
        setToast({ variant: 'danger', message })
      },
    })
  }

  const confirmAction = () => {
    if (pendingAction === null) return
    deleteFriendship.mutate(pendingAction.item.friendshipId, {
      onSuccess: () => {
        setPendingAction(null)
        setToast({ variant: 'success', message: `${ACTION_TEXT[pendingAction.kind].title}했어요.` })
      },
      onError: () =>
        setToast({ variant: 'danger', message: '처리에 실패했어요. 다시 시도해 주세요.' }),
    })
  }

  const accept = (item: FriendshipItem) => {
    acceptRequest.mutate(item.friendshipId, {
      onSuccess: () =>
        setToast({ variant: 'success', message: `${item.user.nickname}님과 친구가 됐어요.` }),
      onError: () =>
        setToast({ variant: 'danger', message: '수락에 실패했어요. 다시 시도해 주세요.' }),
    })
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="font-display text-2xl">친구</h1>

      <form onSubmit={submitRequest} className="mt-4">
        <div className="flex gap-2">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="친구 이메일로 요청…"
            aria-label="친구 이메일"
            className="min-w-0 flex-1 rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm outline-none focus:outline-ring"
          />
          <Button variant="primary" type="submit" disabled={sendRequest.isPending}>
            요청
          </Button>
        </div>
        {emailError !== null && (
          <p role="alert" className="mt-1 text-sm font-semibold text-danger-text">
            {emailError}
          </p>
        )}
      </form>

      <div role="tablist" aria-label="친구 탭" className="mt-5 flex gap-1 rounded-xl border-2 border-border-strong bg-surface p-1">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={`flex-1 rounded-lg px-4 py-1.5 text-sm font-semibold transition duration-150 ${
              tab === t.key
                ? 'bg-accent-deep text-white'
                : 'text-text-secondary hover:bg-surface-muted'
            }`}
          >
            {t.label}
            {t.key === 'received' && received.length > 0 && (
              <span className="ml-1 rounded-full bg-danger px-1.5 text-xs text-danger-text">
                {received.length}
              </span>
            )}
          </button>
        ))}
      </div>

      {tab === 'all' && (
        <>
          {received.length > 0 && (
            <button
              type="button"
              onClick={() => setTab('received')}
              className="mt-4 w-full rounded-2xl border-2 border-border-strong bg-surface-muted px-4 py-3 text-left text-sm font-semibold text-text shadow-brutal-sm transition duration-150 hover:-translate-y-0.5"
            >
              새 친구 요청이 {received.length}건 있어요 — 받은 요청 탭에서 확인하기
            </button>
          )}
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="친구 검색…"
            aria-label="친구 검색"
            className="mt-4 w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2 text-sm outline-none focus:outline-ring"
          />
          {filteredFriends.length === 0 && search !== '' && (
            <div className="mt-4 rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-8 text-center">
              <p className="text-sm font-semibold text-text-secondary">
                '{search}'에 대한 결과가 없습니다
              </p>
              <Button
                variant="ghost"
                size="sm"
                className="mt-2"
                onClick={() => setSearch('')}
              >
                검색어 지우기
              </Button>
            </div>
          )}
          {filteredFriends.length === 0 && search === '' && !friendsQuery.isPending && (
            <div className="mt-4 rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-8 text-center">
              <p className="text-sm font-semibold text-text-secondary">
                아직 친구가 없어요. 이메일로 요청을 보내 보세요.
              </p>
            </div>
          )}
          <ul className="mt-4 space-y-2">
            {filteredFriends.map((f) => (
              <li
                key={f.friendshipId}
                className="flex items-center gap-3 rounded-2xl border-2 border-border-strong bg-surface px-4 py-3 shadow-brutal-sm"
              >
                <Avatar user={f.user} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-text">{f.user.nickname}</p>
                  <p className="truncate text-xs text-text-secondary">{f.user.email}</p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setPendingAction({ kind: 'delete', item: f })}
                >
                  삭제
                </Button>
              </li>
            ))}
          </ul>
        </>
      )}

      {tab === 'received' && (
        <ul className="mt-4 space-y-2">
          {received.length === 0 && (
            <li className="rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-8 text-center text-sm font-semibold text-text-secondary">
              받은 친구 요청이 없어요.
            </li>
          )}
          {received.map((r) => (
            <li
              key={r.friendshipId}
              className="flex items-center gap-3 rounded-2xl border-2 border-border-strong bg-surface px-4 py-3 shadow-brutal-sm"
            >
              <Avatar user={r.user} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-text">{r.user.nickname}</p>
                <p className="truncate text-xs text-text-secondary">{r.user.email}</p>
              </div>
              <Button
                variant="primary"
                size="sm"
                onClick={() => accept(r)}
                disabled={acceptRequest.isPending}
              >
                수락
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setPendingAction({ kind: 'reject', item: r })}
              >
                거절
              </Button>
            </li>
          ))}
        </ul>
      )}

      {tab === 'sent' && (
        <ul className="mt-4 space-y-2">
          {sent.length === 0 && (
            <li className="rounded-2xl border-2 border-dashed border-border-strong bg-surface-muted p-8 text-center text-sm font-semibold text-text-secondary">
              보낸 친구 요청이 없어요.
            </li>
          )}
          {sent.map((r) => (
            <li
              key={r.friendshipId}
              className="flex items-center gap-3 rounded-2xl border-2 border-border-strong bg-surface px-4 py-3 shadow-brutal-sm"
            >
              <Avatar user={r.user} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-text">{r.user.nickname}</p>
                <p className="truncate text-xs text-text-secondary">{r.user.email}</p>
              </div>
              <span className="rounded-lg bg-surface-muted px-2 py-0.5 text-xs font-bold text-text-secondary">
                대기중
              </span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setPendingAction({ kind: 'cancel', item: r })}
              >
                취소
              </Button>
            </li>
          ))}
        </ul>
      )}

      <ConfirmModal
        open={pendingAction !== null}
        onClose={() => setPendingAction(null)}
        onConfirm={confirmAction}
        title={pendingAction !== null ? ACTION_TEXT[pendingAction.kind].title : ''}
        description={
          pendingAction !== null
            ? `${pendingAction.item.user.nickname} — ${ACTION_TEXT[pendingAction.kind].desc}`
            : undefined
        }
        confirmLabel={pendingAction !== null ? ACTION_TEXT[pendingAction.kind].confirm : '확인'}
        tone="danger"
        pending={deleteFriendship.isPending}
      />
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
