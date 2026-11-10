import { Link, useNavigate, useParams } from 'react-router-dom'
import { useFriends } from '../../features/friends/api/use-friends'
import {
  useFeedSubscriptions,
  useSetFeedSubscription,
} from '../../features/friends/api/use-friend-groups'
import { FeedCard } from '../../features/share/components/feed-card'
import { useSharesByUser } from '../../features/share/api/use-share'
import { cx } from '../../utils/cx'
import { EmptyState } from '../ui/empty-state'
import { QueryErrorToast } from '../ui/query-error-toast'
import { SkeletonRow } from '../ui/skeleton-row'
import { Toast } from '../ui/toast'
import { useState } from 'react'

// 친구 프로필 — 친구 정보(friends) + 공개 항목·구독(share/friend-groups)이 섞이므로
// components/share에 둔다. 시안 195:953.
export function FriendProfilePage() {
  const { userId } = useParams<{ userId: string }>()
  const id = Number(userId)
  const navigate = useNavigate()
  const friendsQuery = useFriends()
  // 무효 id는 조회 없이 에러 표시로 종결 (shared-detail-page와 동일 가드)
  const sharesQuery = useSharesByUser(Number.isFinite(id) ? id : 0)
  const subscriptionsQuery = useFeedSubscriptions()
  const setSubscription = useSetFeedSubscription()
  const [toast, setToast] = useState<string | null>(null)

  if (!Number.isFinite(id)) {
    return (
      <div className="w-full">
        <p className="text-sm text-text-secondary">잘못된 프로필 주소예요.</p>
        <div className="mt-4">
          <Link to="/app/friends" className="text-sm font-bold text-accent-text hover:underline">
            친구 목록으로 돌아가기
          </Link>
        </div>
      </div>
    )
  }

  const friend = (friendsQuery.data ?? []).find((f) => f.user.id === id)
  const items = sharesQuery.data ?? []
  const subscribed = (subscriptionsQuery.data ?? []).includes(id)

  const toggle = () => {
    setSubscription.mutate(
      { userId: id, subscribed: !subscribed },
      {
        onError: () => setToast('알림 설정에 실패했어요. 다시 시도해 주세요.'),
      },
    )
  }

  return (
    <div className="w-full">
      <button
        type="button"
        onClick={() => navigate('/app/friends')}
        className="text-sm font-bold text-text-secondary transition duration-150 hover:text-text"
      >
        ← 친구 목록
      </button>

      <div className="mt-5 flex items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          {friend?.user.profileImage ? (
            <img
              src={friend.user.profileImage}
              alt=""
              className="size-16 rounded-full border-2 border-border-strong object-cover"
            />
          ) : (
            <span
              aria-hidden="true"
              className="flex size-16 items-center justify-center rounded-full border-2 border-surface bg-primary text-xl font-bold text-on-tint"
            >
              {(friend?.user.nickname ?? '?').slice(0, 1)}
            </span>
          )}
          <div>
            <h1 className="font-display text-2xl">{friend?.user.nickname ?? '친구'}</h1>
            <p className="text-sm font-medium text-text-secondary">
              친구 · 공개 항목 {items.length}개
            </p>
          </div>
        </div>
        <label className="flex items-center gap-2.5 text-sm font-bold text-text-secondary">
          피드 알림
          <button
            type="button"
            role="switch"
            aria-checked={subscribed}
            aria-label="피드 알림"
            onClick={toggle}
            disabled={setSubscription.isPending}
            className={cx(
              'relative h-6 w-11 rounded-full transition duration-150',
              subscribed ? 'bg-accent' : 'bg-surface-muted',
            )}
          >
            <span
              aria-hidden="true"
              className={cx(
                'absolute top-0.5 size-5 rounded-full bg-surface shadow-card transition duration-150',
                subscribed ? 'left-5.5' : 'left-0.5',
              )}
            />
          </button>
        </label>
      </div>

      <h2 className="mt-8 text-base font-bold text-text">
        공개 항목{' '}
        <span className="ml-1 text-sm font-medium text-text-secondary">
          {friend?.user.nickname ?? '친구'}님이 공개한 일기·일정
        </span>
      </h2>

      <div className="mt-4">
        {sharesQuery.isPending && <SkeletonRow rows={3} rowClassName="h-24" />}
        <QueryErrorToast
          show={sharesQuery.isError}
          message="공개 항목을 불러오지 못했어요. 친구가 아니면 볼 수 없어요."
        />
        {sharesQuery.data !== undefined && items.length === 0 && (
          <EmptyState
            title="공개된 항목이 없어요"
            description="친구가 일기·일정을 공개하면 여기에 보여요"
          />
        )}
        {items.length > 0 && (
          <ul className="flex flex-col gap-5">
            {items.map((item) => (
              <FeedCard key={item.shareId} item={item} showOwner={false} />
            ))}
          </ul>
        )}
      </div>

      {/* 친구를 못 찾았을 때(비친구·직접 URL) 목록으로 유도 */}
      {friendsQuery.data !== undefined && friend === undefined && (
        <div className="mt-8 text-center">
          <Link to="/app/friends" className="text-sm font-bold text-accent-text hover:underline">
            친구 목록으로 돌아가기
          </Link>
        </div>
      )}
      {toast !== null && (
        <Toast variant="danger" message={toast} onClose={() => setToast(null)} />
      )}
    </div>
  )
}
