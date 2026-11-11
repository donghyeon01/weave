import { Link } from 'react-router-dom'
import { cx } from '../../../utils/cx'
import { ITEM_TYPE_LABEL, type FeedItem } from '../types'
import { relativeTime } from '../time'

// 시안 195:597 — 피드 카드 우측 타입 칩 (일기=라벤더, 일정=민트).
const CHIP_CLASS: Record<FeedItem['itemType'], string> = {
  DIARY: 'bg-accent-soft text-on-tint',
  EVENT: 'bg-tint-mint text-on-success',
}

function MiniAvatar({ nickname }: { nickname: string }) {
  return (
    <span
      aria-hidden="true"
      className="flex size-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-dark"
    >
      {nickname.slice(0, 1)}
    </span>
  )
}

/**
 * 피드·친구 프로필 공용 공유 항목 카드.
 * showOwner=false면 친구 프로필 레이아웃(작성자 행 생략, 칩+제목 한 줄) — 시안 195:953.
 */
export function FeedCard({ item, showOwner = true }: { item: FeedItem; showOwner?: boolean }) {
  return (
    <li>
      <Link
        to={`/app/shared/${item.shareId}`}
        className="block rounded-xl border border-border bg-surface p-5 shadow-card transition duration-150 hover:bg-surface-muted/60"
      >
        {showOwner ? (
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <MiniAvatar nickname={item.owner.nickname} />
              <span className="text-sm font-bold text-text">{item.owner.nickname}</span>
              <span className="text-xs text-text-muted">{relativeTime(item.createdAt)}</span>
            </div>
            <span
              className={cx(
                'shrink-0 rounded-full px-3 py-1 text-xs font-bold',
                CHIP_CLASS[item.itemType],
              )}
            >
              {ITEM_TYPE_LABEL[item.itemType]}
            </span>
          </div>
        ) : (
          <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <span
                className={cx(
                  'shrink-0 rounded-full px-3 py-1 text-xs font-bold',
                  CHIP_CLASS[item.itemType],
                )}
              >
                {ITEM_TYPE_LABEL[item.itemType]}
              </span>
              <span className="min-w-0 truncate text-sm font-bold text-text">
                {item.itemTitle ?? '제목 없음'}
              </span>
            </div>
            <span className="shrink-0 text-xs text-text-muted">답글 {item.replyCount}</span>
          </div>
        )}
        {showOwner && (
          <p className="mt-3 truncate text-sm font-bold text-text">
            {item.itemTitle ?? '제목 없음'}
          </p>
        )}
        {item.itemPreview !== null && item.itemPreview !== '' && (
          <p className="mt-1.5 truncate text-sm text-text-secondary">{item.itemPreview}</p>
        )}
        {showOwner && (
          <p className="mt-2 text-xs font-medium text-text-muted">
            답글 {item.replyCount} · 열기
          </p>
        )}
      </Link>
    </li>
  )
}
