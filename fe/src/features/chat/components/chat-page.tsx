import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../../components/ui/empty-state'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { cx } from '../../../utils/cx'
import { useChatRooms } from '../api/use-chat'
import type { ChatRoomSummary, RoomStatus } from '../types'

type Tab = 'OPEN' | 'CLOSED'

const TABS: { key: Tab; label: string }[] = [
  { key: 'OPEN', label: '진행 중' },
  { key: 'CLOSED', label: '지난 채팅방' },
]

function formatTime(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// Figma 180:1751/184:547 — 채팅방 목록. 상태별 세그먼트 + 카드 리스트.
function RoomCard({ room }: { room: ChatRoomSummary }) {
  const closed = room.status === 'CLOSED'
  return (
    <li>
      <Link
        to={`/app/chat/${room.roomId}`}
        className="flex flex-col gap-1.5 rounded-xl border border-border bg-surface p-4 shadow-card transition duration-150 hover:bg-surface-muted/60"
      >
        <div className="flex items-center justify-between gap-3">
          <p className="min-w-0 truncate text-base font-bold text-text">{room.title}</p>
          <span className="flex shrink-0 items-center gap-1.5">
            {room.unreadCount > 0 && (
              <>
                <span aria-hidden="true" className="size-2 rounded-full bg-accent" />
                <span className="text-sm font-bold text-accent-text">{room.unreadCount}</span>
              </>
            )}
            {room.lastMessageAt !== undefined && (
              <span className="text-xs font-medium text-text-muted">
                {formatTime(room.lastMessageAt)}
              </span>
            )}
          </span>
        </div>
        <p className="truncate text-sm font-medium text-text-secondary">
          {closed ? '대화가 종료됐어요' : (room.lastMessage ?? '아직 메시지가 없어요')}
        </p>
      </Link>
    </li>
  )
}

export function ChatPage() {
  const [tab, setTab] = useState<Tab>('OPEN')
  const roomsQuery = useChatRooms()

  const rooms = roomsQuery.data ?? []
  const countOf = (status: RoomStatus) => rooms.filter((r) => r.status === status).length
  const visible = rooms.filter((r) => r.status === tab)

  return (
    <div className="w-full">
      <div className="flex flex-col gap-2.5">
        <h1 className="font-display text-[32px] leading-normal">채팅</h1>
        <p className="text-base font-medium text-text-secondary">
          확정을 준비 중인 약속의 단체 채팅방이에요
        </p>
      </div>

      <div
        role="tablist"
        aria-label="채팅방 탭"
        className="mt-6 inline-flex items-center gap-1 rounded-lg border border-border bg-background p-1.5"
      >
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={cx(
              'rounded-md px-4 py-1.5 text-sm font-bold transition duration-150',
              tab === t.key
                ? 'border border-border bg-surface text-accent-text shadow-sm'
                : 'text-text-secondary hover:text-text',
            )}
          >
            {t.label}{' '}
            <span className={tab === t.key ? 'text-accent-text' : 'text-text-muted'}>
              ({countOf(t.key)})
            </span>
          </button>
        ))}
      </div>

      {roomsQuery.isPending && <SkeletonRow rows={3} className="mt-6" />}
      <QueryErrorToast
        show={roomsQuery.isError}
        message="채팅방을 불러오지 못했어요. 다시 시도해 주세요."
      />

      {visible.length > 0 && (
        <>
          <p className="mt-6 text-sm font-semibold text-text-secondary">
            채팅방 {visible.length}
          </p>
          <ul className="mt-3 flex flex-col gap-3">
            {visible.map((room) => (
              <RoomCard key={room.roomId} room={room} />
            ))}
          </ul>
        </>
      )}
      {roomsQuery.data !== undefined && visible.length === 0 && (
        <EmptyState
          className="mt-6"
          title={tab === 'OPEN' ? '진행 중인 채팅방이 없어요' : '지난 채팅방이 없어요'}
          description={
            tab === 'OPEN'
              ? '약속이 확정되면 단체 채팅방이 여기에 열려요'
              : '종료된 채팅방은 여기에 보관돼요'
          }
        />
      )}
    </div>
  )
}
