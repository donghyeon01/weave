import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge } from '../../../components/ui/badge'
import { Button } from '../../../components/ui/button'
import { ConfirmModal } from '../../../components/ui/confirm-modal'
import { Modal } from '../../../components/ui/modal'
import { Toast } from '../../../components/ui/toast'
import { ApiError } from '../../../lib/api-client'
import { cx } from '../../../utils/cx'
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
import { useFriendGroups } from '../api/use-friend-groups'
import type { FriendshipItem } from '../types'
import { GroupsTab } from './groups-tab'
import { ShareFeedPanel } from '../../../components/share/feed-panel'
import { EmptyState } from '../../../components/ui/empty-state'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'
import { SkeletonRow } from '../../../components/ui/skeleton-row'

type Tab = 'all' | 'received' | 'sent' | 'groups' | 'feed'

const TAB_KEYS: readonly Tab[] = ['all', 'received', 'sent', 'groups', 'feed']

function parseTab(param: string | null): Tab {
  return TAB_KEYS.find((t) => t === param) ?? 'all'
}

type PendingAction =
  | { kind: 'reject'; item: FriendshipItem }
  | { kind: 'cancel'; item: FriendshipItem }
  | { kind: 'delete'; item: FriendshipItem }

const ACTION_TEXT: Record<PendingAction['kind'], { title: string; confirm: string; desc: string }> = {
  reject: { title: '요청 거절', confirm: '거절', desc: '받은 친구 요청을 거절할까요?' },
  cancel: { title: '요청 취소', confirm: '취소', desc: '보낸 친구 요청을 취소할까요?' },
  delete: { title: '친구 삭제', confirm: '삭제', desc: '친구 목록에서 삭제할까요?' },
}

function Avatar({ user, size = 'md' }: { user: FriendshipItem['user']; size?: 'md' | 'lg' }) {
  const cls =
    size === 'lg'
      ? 'size-12 text-base'
      : 'size-10 text-sm'
  if (user.profileImage !== null) {
    return (
      <img
        src={user.profileImage}
        alt=""
        className={`${cls} rounded-full border-2 border-border-strong object-cover`}
      />
    )
  }
  return (
    <span
      aria-hidden="true"
      className={`flex ${cls} items-center justify-center rounded-full border-2 border-surface bg-primary font-bold text-on-tint`}
    >
      {user.nickname.slice(0, 1)}
    </span>
  )
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 15 15" className="size-3.75" aria-hidden="true">
      <path
        fill="currentColor"
        d="M10.7 9.6a6 6 0 1 0-1.1 1.1L14 15l1-1-4.3-4.4ZM6 10a4 4 0 1 1 0-8 4 4 0 0 1 0 8Z"
      />
    </svg>
  )
}

function KebabIcon() {
  return (
    <svg viewBox="0 0 4 14" className="h-3.5 w-1" aria-hidden="true">
      <circle cx="2" cy="2" r="2" fill="currentColor" />
      <circle cx="2" cy="7" r="2" fill="currentColor" />
      <circle cx="2" cy="12" r="2" fill="currentColor" />
    </svg>
  )
}

// Figma 4:1215 — 헤더(제목+총N명 배지+검색+친구 초대) · 세그먼트 탭 · 요청 배너 · 3열 친구 카드.
// 상태 메시지·접속 표시(presence)는 V2 이연이라 렌더하지 않는다 (이탈 원장).
export function FriendsPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const friendsQuery = useFriends()
  const receivedQuery = useReceivedRequests()
  const sentQuery = useSentRequests()
  const groupsQuery = useFriendGroups()
  const sendRequest = useSendRequest()
  const acceptRequest = useAcceptRequest()
  const deleteFriendship = useDeleteFriendship()

  // 탭 상태는 ?tab=이 진실 원천 — 딥링크·탭 전환 모두 URL과 동기화 (replace로 이력 오염 방지)
  const tab = parseTab(searchParams.get('tab'))
  const setTab = (t: Tab) =>
    setSearchParams(t === 'all' ? {} : { tab: t }, { replace: true })
  const [inviteOpen, setInviteOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [menuFor, setMenuFor] = useState<number | null>(null)
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
        setInviteOpen(false)
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

  const groupCount = groupsQuery.data?.length ?? 0
  // 시안 194:545 — 5개 세그먼트. 피드는 건수를 세지 않는다 (시안에 카운트 없음).
  const tabs: { key: Tab; label: string }[] = [
    { key: 'all', label: `전체 친구 (${friends.length})` },
    { key: 'received', label: `받은 요청 (${received.length})` },
    { key: 'sent', label: `보낸 요청 (${sent.length})` },
    { key: 'groups', label: `그룹 (${groupCount})` },
    { key: 'feed', label: '피드' },
  ]

  return (
    <div className="w-full">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-[32px] leading-10">내 친구</h1>
          <Badge variant="accent">총 {friends.length}명</Badge>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative w-72">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary">
              <SearchIcon />
            </span>
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="친구 검색..."
              aria-label="친구 검색"
              className="w-full rounded-lg border border-border bg-surface py-2.5 pl-10 pr-4 text-sm font-bold text-text-secondary outline-none focus:outline-2 focus:outline-solid focus:outline-ring"
            />
          </div>
          <Button variant="primary" size="sm" onClick={() => setInviteOpen(true)}>
            <svg viewBox="0 0 14 14" className="h-3.5 w-3.5" aria-hidden="true">
              <path
                fill="currentColor"
                d="M9.5 8.5a4 4 0 1 0-4-4H4a4 4 0 0 0 5.5 4ZM2 11c0-2 2-3 4-3s4 1 4 3v2H2v-2Zm10-6h-2V3h-2V1.5h2V-.5h2V1.5h2V3h-2v2Z"
              />
            </svg>
            친구 초대
          </Button>
        </div>
      </div>

      <div
        role="tablist"
        aria-label="친구 탭"
        className="mt-6 flex items-center gap-2 border-b border-border pb-3"
      >
        {tabs.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={cx(
              'rounded-lg px-4 py-2 text-sm font-bold transition duration-150',
              tab === t.key
                ? 'border border-border bg-surface-muted text-accent-text shadow-sm'
                : 'text-text-secondary hover:text-text',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'all' && (
        <>
          {received.length > 0 && (
            <div className="mt-6 rounded-xl border border-border bg-tint-peach p-5 text-on-tint">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <svg viewBox="0 0 20 18" className="h-4.5 w-5" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M10 1 0 5.5v2h2v9h16v-9h2v-2L10 1Zm0 3.4L15.6 8H4.4L10 4.4ZM4 9.5h12V15H4V9.5Z"
                    />
                  </svg>
                  <h2 className="font-display text-xl">새로운 친구 요청이 {received.length}건 있어요</h2>
                </div>
                <span className="text-xs font-medium text-on-tint/70">
                  최근 3일 내 도착한 요청
                </span>
              </div>
              <ul className="mt-4 flex flex-col gap-3">
                {received.map((r) => (
                  <li
                    key={r.friendshipId}
                    className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3"
                  >
                    <Avatar user={r.user} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-text">{r.user.nickname}</p>
                      <p className="truncate text-xs text-text-secondary">
                        {r.message ?? r.user.email}
                      </p>
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
            </div>
          )}

          {filteredFriends.length === 0 && search !== '' && (
            <>
              <EmptyState
                className="mt-6"
                title="검색 결과가 없습니다"
                description={`'${search}'와 일치하는 친구가 없어요`}
              />
              <div className="mt-2 text-center">
                <Button variant="ghost" size="sm" onClick={() => setSearch('')}>
                  검색어 지우기
                </Button>
              </div>
            </>
          )}
          {filteredFriends.length === 0 && search === '' && !friendsQuery.isPending && (
            <EmptyState
              className="mt-6"
              title="아직 친구가 없어요"
              description="이메일로 요청을 보내 보세요"
            />
          )}

          {/* Figma 116:2536 — 로딩 중 카드형 스켈레톤 3블록 */}
          {friendsQuery.isPending && (
            <SkeletonRow rows={3} rowClassName="h-56 w-72" className="mt-6 gap-6" />
          )}

          <ul className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
            {filteredFriends.map((f) => (
              <li
                key={f.friendshipId}
                className="flex flex-col justify-between rounded-lg border border-border bg-surface p-5 shadow-card-sm"
              >
                <div className="flex items-start justify-between">
                  <button
                    type="button"
                    onClick={() => navigate(`/app/friends/${f.user.id}`)}
                    aria-label={`${f.user.nickname} 프로필`}
                    className="flex items-center gap-3 text-left transition duration-150 hover:opacity-80"
                  >
                    <Avatar user={f.user} size="lg" />
                    <p className="text-sm font-bold text-text">{f.user.nickname}</p>
                  </button>
                  <div className="relative">
                    <button
                      type="button"
                      aria-label={`${f.user.nickname} 옵션`}
                      onClick={() => setMenuFor(menuFor === f.friendshipId ? null : f.friendshipId)}
                      className="flex size-7.5 items-center justify-center rounded-md text-text-secondary hover:bg-surface-muted"
                    >
                      <KebabIcon />
                    </button>
                    {menuFor === f.friendshipId && (
                      <div className="absolute right-0 top-8 z-10 w-28 rounded-lg border border-border bg-surface py-1 shadow-md">
                        <button
                          type="button"
                          className="w-full px-3 py-1.5 text-left text-sm text-danger-text hover:bg-surface-muted"
                          onClick={() => {
                            setMenuFor(null)
                            setPendingAction({ kind: 'delete', item: f })
                          }}
                        >
                          친구 끊기
                        </button>
                      </div>
                    )}
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-2">
                  <svg viewBox="0 0 12 13" className="h-3.5 w-3" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M4 0v1.5h4V0h1v1.5h1.5A1.5 1.5 0 0 1 12 3v8.5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 0 11.5V3a1.5 1.5 0 0 1 1.5-1.5H3V0h1Zm6.5 5h-9v6.5h9V5Z"
                    />
                  </svg>
                  {f.nextEvent ? (
                    <span className="rounded-md bg-tint-mint px-2.5 py-1 text-xs font-bold text-on-success">
                      {new Date(f.nextEvent.startTime).toLocaleDateString('ko-KR', {
                        month: 'numeric',
                        day: 'numeric',
                      })}{' '}
                      {f.nextEvent.title}
                    </span>
                  ) : (
                    <span className="text-xs font-medium text-text-muted">예정된 약속 없음</span>
                  )}
                </div>
                <div className="mt-5 border-t border-border pt-3">
                  <Button
                    variant="ghost"
                    size="md"
                    fullWidth
                    onClick={() => navigate('/app/scheduling')}
                  >
                    <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true">
                      <path
                        fill="currentColor"
                        d="M5.33 0v2h5.34V0h1.33v2H13.5A1.5 1.5 0 0 1 15 3.5v11a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 14.5v-11A1.5 1.5 0 0 1 2.5 2H4V0h1.33Zm8.17 5.33h-11v9.17h11V5.33Z"
                      />
                    </svg>
                    일정 같이 잡기
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      {tab === 'received' && (
        <ul className="mt-6 space-y-2">
          {received.length === 0 && (
            <EmptyState title="받은 친구 요청이 없어요" description="새 요청이 오면 여기에 표시됩니다" />
          )}
          {received.map((r) => (
            <li
              key={r.friendshipId}
              className="flex items-center gap-3 rounded-2xl border-2 border-border-strong bg-surface px-4 py-3 shadow-brutal-sm"
            >
              <Avatar user={r.user} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-text">{r.user.nickname}</p>
                <p className="truncate text-xs text-text-secondary">
                  {r.message ?? r.user.email}
                </p>
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
        <ul className="mt-6 space-y-2">
          {sent.length === 0 && (
            <EmptyState title="보낸 친구 요청이 없어요" description="친구 초대로 새 요청을 보내 보세요" />
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
              <Badge>대기중</Badge>
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

      {tab === 'groups' && (
        <GroupsTab onToast={(message) => setToast({ variant: 'success', message })} />
      )}

      {tab === 'feed' && <ShareFeedPanel />}

      {/* 친구 초대 모달 — 이메일 입력 (patterns.md 확정 인터랙션) */}
      <Modal
        open={inviteOpen}
        onClose={() => {
          setInviteOpen(false)
          setEmail('')
          setEmailError(null)
        }}
        title={<h2 className="font-display text-lg">친구 초대</h2>}
      >
        <form onSubmit={submitRequest}>
          <label className="block text-sm font-bold text-text">
            이메일
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="친구 이메일을 입력하세요"
              aria-label="친구 이메일"
              className="mt-1.5 w-full rounded-xl border-2 border-border-strong bg-surface px-3 py-2.5 text-[15px] font-medium outline-none focus:outline-2 focus:outline-solid focus:outline-ring"
            />
          </label>
          {emailError !== null && (
            <p role="alert" className="mt-1 text-sm font-semibold text-danger-text">
              {emailError}
            </p>
          )}
          <div className="mt-4 flex justify-end gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setInviteOpen(false)
                setEmail('')
                setEmailError(null)
              }}
            >
              취소
            </Button>
            <Button variant="primary" size="sm" type="submit" disabled={sendRequest.isPending}>
              요청 보내기
            </Button>
          </div>
        </form>
      </Modal>

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
      <QueryErrorToast
        show={friendsQuery.isError}
        message="친구 목록을 불러오지 못했어요. 다시 시도해 주세요."
      />
      {toast !== null && (
        <Toast variant={toast.variant} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
