import { Button } from '../ui/button'
import { EmptyState } from '../ui/empty-state'
import { QueryErrorToast } from '../ui/query-error-toast'
import { SkeletonRow } from '../ui/skeleton-row'
import { FeedCard } from '../../features/share/components/feed-card'
import { useShareFeed } from '../../features/share/api/use-share'

// 친구 화면 "피드" 탭 — share 데이터지만 friends feature에 임베드되므로
// components/share(교차 feature UI 위치)에 둔다. 시안 195:597.
export function ShareFeedPanel() {
  const feedQuery = useShareFeed()
  const items = feedQuery.data?.pages.flat() ?? []

  return (
    <div className="mt-6">
      {feedQuery.isPending && <SkeletonRow rows={3} rowClassName="h-32" />}
      <QueryErrorToast
        show={feedQuery.isError}
        message="피드를 불러오지 못했어요. 다시 시도해 주세요."
      />
      {feedQuery.data !== undefined && items.length === 0 && (
        <EmptyState
          title="아직 공개된 항목이 없어요"
          description="친구가 일기·일정을 공개하면 여기에 보여요"
        />
      )}
      {items.length > 0 && (
        <ul className="flex flex-col gap-5">
          {items.map((item) => (
            <FeedCard key={item.shareId} item={item} />
          ))}
        </ul>
      )}
      {feedQuery.hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => feedQuery.fetchNextPage()}
            disabled={feedQuery.isFetchingNextPage}
          >
            {feedQuery.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
          </Button>
        </div>
      )}
    </div>
  )
}
