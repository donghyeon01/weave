import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '../../../components/ui/button'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { VisibilityModal } from '../../../components/share/visibility-modal'
import { queryKeys } from '../../../lib/react-query'
import type { UserResponse } from '../../../types/auth'
import { useShareDetail, useSharePhotoUrl } from '../api/use-share'
import type { SharedDiaryItem, SharedEventItem, ShareVisibility } from '../types'
import { ITEM_TYPE_LABEL } from '../types'
import { ReplyThread } from './reply-thread'

const VISIBILITY_LABEL: Record<ShareVisibility, string> = {
  PRIVATE: '비공개',
  FRIENDS: '전체 친구',
  GROUP: '특정 그룹',
  PUBLIC: '전체 공개',
}

// diary feature의 mood-picker 라벨과 동일 — feature 간 import 금지라 여기서 별도 유지한다.
const MOOD_LABEL: Record<string, string> = {
  happy: '행복해요',
  calm: '평온해요',
  busy: '바빴어요',
  tired: '힘들었어요',
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

function diaryDateLine(ymd: string): string {
  const [y, m, d] = ymd.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${m}월 ${d}일 (${weekday})`
}

function eventTimeLine(e: SharedEventItem): string {
  const start = new Date(e.startTime)
  const end = new Date(e.endTime)
  const day = `${start.getMonth() + 1}월 ${start.getDate()}일 (${WEEKDAYS[start.getDay()]})`
  if (e.allDay) return `${day} 종일`
  const t = (d: Date) =>
    `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return `${day} ${t(start)}–${t(end)}`
}

function SharedPhoto({ shareId, photoId }: { shareId: number; photoId: number }) {
  const url = useSharePhotoUrl(shareId, photoId)
  if (url === null) {
    return <div className="size-20 rounded-xl bg-surface-muted" aria-hidden="true" />
  }
  return <img src={url} alt="공유 일기 사진" className="size-20 rounded-xl object-cover" />
}

function DiaryEmbed({ shareId, item }: { shareId: number; item: SharedDiaryItem }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-5 shadow-card">
      <div className="flex items-center gap-2">
        <span className="text-sm font-bold text-text">{diaryDateLine(item.date)}</span>
        {item.mood !== null && MOOD_LABEL[item.mood] !== undefined && (
          <span className="rounded-full bg-tint-mint px-2.5 py-0.5 text-xs font-bold text-on-success">
            {MOOD_LABEL[item.mood]}
          </span>
        )}
      </div>
      {item.title !== null && (
        <h2 className="mt-3 text-lg font-bold text-text">{item.title}</h2>
      )}
      <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-text">{item.content}</p>
      {item.photos.length > 0 && (
        <div className="mt-3 flex gap-3">
          {item.photos.map((p) => (
            <SharedPhoto key={p.id} shareId={shareId} photoId={p.id} />
          ))}
        </div>
      )}
      {item.tags !== null && item.tags.length > 0 && (
        <p className="mt-3 text-sm font-semibold text-accent-text">
          {item.tags.map((t) => `#${t}`).join(' ')}
        </p>
      )}
    </div>
  )
}

function EventEmbed({ item }: { item: SharedEventItem }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-5 shadow-card">
      <h2 className="text-lg font-bold text-text">{item.title}</h2>
      <p className="mt-2 text-sm text-text-secondary">{eventTimeLine(item)}</p>
      {item.location !== null && item.location !== '' && (
        <p className="mt-1 text-sm text-text-secondary">장소 · {item.location}</p>
      )}
      {item.description !== null && item.description !== '' && (
        <p className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-text">
          {item.description}
        </p>
      )}
    </div>
  )
}

// 시안 195:1311 — ← 피드 · "님이 공개한 일기 · 전체 친구" 캡션 · 항목 임베드 카드 · 답글 스레드.
export function SharedDetailPage() {
  const { shareId } = useParams<{ shareId: string }>()
  const id = shareId !== undefined ? Number(shareId) : undefined
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const me = queryClient.getQueryData<UserResponse>(queryKeys.users.me)
  const detailQuery = useShareDetail(Number.isFinite(id) ? id : undefined)
  const [visibilityOpen, setVisibilityOpen] = useState(false)

  const detail = detailQuery.data
  const isOwner = detail !== undefined && me?.id === detail.owner.id

  return (
    <div className="w-full">
      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="text-sm font-bold text-text-secondary transition duration-150 hover:text-text"
        >
          ← 피드
        </button>
        {detail !== undefined && (
          <div className="flex items-center gap-3">
            <p className="text-xs text-text-muted">
              {detail.owner.nickname}님이 공개한 {ITEM_TYPE_LABEL[detail.itemType]} ·{' '}
              {VISIBILITY_LABEL[detail.visibility]}
            </p>
            {isOwner && (
              <Button variant="ghost" size="sm" onClick={() => setVisibilityOpen(true)}>
                공개 범위
              </Button>
            )}
          </div>
        )}
      </div>

      {detailQuery.isPending && <SkeletonRow rows={2} rowClassName="h-40" className="mt-6" />}
      <QueryErrorToast
        show={detailQuery.isError}
        message="공유 항목을 불러오지 못했어요. 공개 범위가 바뀌었거나 삭제됐을 수 있어요."
      />

      {detail !== undefined && (
        <div className="mt-6 flex flex-col gap-8">
          {detail.itemType === 'DIARY' ? (
            <DiaryEmbed shareId={detail.shareId} item={detail.item as SharedDiaryItem} />
          ) : (
            <EventEmbed item={detail.item as SharedEventItem} />
          )}
          <section>
            <h2 className="text-base font-bold text-text">답글 {detail.replyCount}</h2>
            <div className="mt-4">
              <ReplyThread shareId={detail.shareId} shareOwnerId={detail.owner.id} />
            </div>
          </section>
        </div>
      )}

      {visibilityOpen && detail !== undefined && (
        <VisibilityModal
          open
          onClose={() => setVisibilityOpen(false)}
          itemType={detail.itemType}
          itemId={detail.itemId}
        />
      )}
    </div>
  )
}
