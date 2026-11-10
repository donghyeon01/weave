import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '../../../components/ui/button'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { queryKeys } from '../../../lib/react-query'
import { subscribeTopic } from '../../../lib/stomp'
import type { UserResponse } from '../../../types/auth'
import { cx } from '../../../utils/cx'
import { useChatMessages, useChatRooms, useMarkRead, useSendMessage } from '../api/use-chat'
import type { ChatMessage, Slot } from '../types'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

const INPUT_CLASS =
  'w-full rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm text-text outline-none transition duration-150 focus:border-accent disabled:cursor-not-allowed disabled:opacity-60'

// "5월 18일 (일) 12:30–14:00" — 제안 보관함 행 표기와 동일 포맷
function formatSlotHuman(slot: Slot): string {
  if (slot.type === 'DATE') return `${slot.startDate} ~ ${slot.endDate}`
  const d = new Date(`${slot.date}T00:00:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]}) ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function MessageBubble({ message, mine }: { message: ChatMessage; mine: boolean }) {
  if (mine) {
    return (
      <li className="flex justify-end">
        <div className="flex max-w-[75%] flex-col items-end gap-1">
          <p className="whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-accent-deep px-3.5 py-2 text-sm font-medium text-white">
            {message.content}
          </p>
          <span className="text-xs font-medium text-text-muted">
            {formatTime(message.createdAt)}
          </span>
        </div>
      </li>
    )
  }
  return (
    <li className="flex items-start gap-2.5">
      <span
        aria-hidden="true"
        className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-on-tint"
      >
        {message.nickname.slice(0, 1)}
      </span>
      <div className="flex max-w-[75%] flex-col gap-1">
        <span className="text-xs font-bold text-text-secondary">{message.nickname}</span>
        <p className="whitespace-pre-wrap break-words rounded-2xl rounded-tl-sm bg-surface-muted px-3.5 py-2 text-sm font-medium text-text">
          {message.content}
        </p>
        <span className="text-xs font-medium text-text-muted">
          {formatTime(message.createdAt)}
        </span>
      </div>
    </li>
  )
}

// Figma 170:568/171:753 — 채팅방 상세. 목록 되돌아가기 + 메타 + 스트림 + 입력 바.
export function ChatRoomPage() {
  const params = useParams()
  const roomId = Number(params.roomId)
  const queryClient = useQueryClient()

  const roomsQuery = useChatRooms()
  const messagesQuery = useChatMessages(Number.isFinite(roomId) ? roomId : null)
  const send = useSendMessage(roomId)
  const markRead = useMarkRead(roomId)

  const [content, setContent] = useState('')
  const [sendError, setSendError] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  // 같은 마지막 메시지로 read POST를 반복하지 않기 위한 가드
  const lastReadSentRef = useRef<number | null>(null)

  const room = roomsQuery.data?.find((r) => r.roomId === roomId)
  const messages = messagesQuery.data ?? []
  const closed = room?.status === 'CLOSED'

  const me = queryClient.getQueryData<UserResponse>(queryKeys.users.me)

  // 방 입장 시 /topic/rooms/{id} 구독 — 수신 메시지는 목록·방 목록 무효화로 반영한다.
  // 방 목록에 없는 방(비멤버·미존재)은 구독하지 않는다 — 서버 거부(ERROR+세션 종료)가
  // 재연결 재구독과 결합해 무한 폭풍이 되는 것을 차단. cleanup으로 구독을 해제한다.
  const roomKnown = room !== undefined
  useEffect(() => {
    if (!Number.isFinite(roomId) || !roomKnown) return
    return subscribeTopic(`/topic/rooms/${roomId}`, () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.messages(roomId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.rooms })
    })
  }, [roomId, roomKnown, queryClient])

  // 최신 메시지까지 읽음 처리 — 보고 있는 방의 unread를 0으로 만든다
  const latestId = messages.length > 0 ? messages[messages.length - 1].id : null
  useEffect(() => {
    if (latestId === null || lastReadSentRef.current === latestId) return
    lastReadSentRef.current = latestId
    markRead.mutate(latestId)
  }, [latestId, markRead])

  // 새 메시지가 오면 스트림 하단으로 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length])

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = content.trim()
    if (trimmed.length === 0 || closed || send.isPending) return
    send.mutate(
      { content: trimmed, clientMessageId: crypto.randomUUID() },
      {
        onSuccess: () => {
          setContent('')
          setSendError(null)
        },
        onError: () => setSendError('메시지를 보내지 못했어요. 잠시 후 다시 시도해 주세요.'),
      },
    )
  }

  return (
    <div className="flex w-full flex-col">
      <div className="flex items-center gap-3">
        <Link
          to="/app/chat"
          className="inline-flex items-center gap-2 rounded-xl border-2 border-border-strong bg-surface px-3 py-1.5 text-sm font-semibold text-text shadow-brutal-sm transition duration-200 hover:bg-surface-muted"
        >
          ← 목록
        </Link>
      </div>

      <div className="mt-4 flex flex-col gap-1.5">
        <h1 className="font-display text-[28px] leading-normal">
          {room?.title ?? '채팅방'}
        </h1>
        {room !== undefined && (
          <p className="text-sm font-medium text-text-secondary">
            {formatSlotHuman(room.slot)}
            {room.memberCount !== undefined && ` · 멤버 ${room.memberCount}명`}
          </p>
        )}
      </div>

      {closed && (
        <div className="mt-4 rounded-xl border border-border bg-surface-muted px-4 py-3 text-sm font-semibold text-text-secondary">
          종료된 채팅방이에요. 읽기만 가능합니다
        </div>
      )}

      {messagesQuery.isPending && <SkeletonRow rows={4} className="mt-6" />}
      <QueryErrorToast
        show={messagesQuery.isError}
        message="메시지를 불러오지 못했어요. 다시 시도해 주세요."
      />

      <ul className="mt-4 flex flex-col gap-4">
        {messages.map((m) => (
          <MessageBubble key={m.id} message={m} mine={m.senderId === me?.id} />
        ))}
      </ul>
      <div ref={bottomRef} />

      <form onSubmit={onSubmit} className="sticky bottom-0 mt-6 flex gap-2 bg-background pb-2 pt-2">
        <input
          type="text"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={2000}
          disabled={closed}
          placeholder={
            closed ? '대화가 종료되어 메시지를 보낼 수 없어요' : '메시지를 입력하세요'
          }
          aria-label="메시지 입력"
          className={cx(INPUT_CLASS, 'flex-1')}
        />
        <Button type="submit" variant="brutal" size="md" disabled={closed || send.isPending}>
          전송
        </Button>
      </form>
      {sendError !== null && (
        <p role="alert" className="mt-2 text-sm font-semibold text-danger-text">
          {sendError}
        </p>
      )}
    </div>
  )
}
