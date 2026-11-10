import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '../../../components/ui/button'
import { QueryErrorToast } from '../../../components/ui/query-error-toast'
import { SkeletonRow } from '../../../components/ui/skeleton-row'
import { queryKeys } from '../../../lib/react-query'
import { onStompConnect, onStompError, subscribeTopic } from '../../../lib/stomp'
import type { UserResponse } from '../../../types/auth'
import { cx } from '../../../utils/cx'
import {
  appendRoomMessage,
  useChatMessages,
  useChatRooms,
  useMarkRead,
  useSendMessage,
} from '../api/use-chat'
import type { ChatMessage, Slot } from '../types'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

const INPUT_CLASS =
  'w-full rounded-xl border-2 border-input bg-surface px-3 py-2 text-sm text-text outline-none transition duration-150 focus:border-accent disabled:cursor-not-allowed disabled:opacity-60'

// Map으로 둔다 — object 리터럴은 서버 code가 'toString' 같은 프로토타입 키일 때
// 함수를 반환해 React child로 렌더되며 크래시한다
const SEND_ERROR_MESSAGES: ReadonlyMap<string, string> = new Map([
  ['CHAT-ROOM-CLOSED', '채팅방이 종료되어 메시지를 보낼 수 없어요'],
  ['CHAT-NOT-MEMBER', '이 채팅방에는 메시지를 보낼 수 없어요'],
  ['CHAT-ROOM-NOT-FOUND', '이 채팅방에는 메시지를 보낼 수 없어요'],
  ['CHAT-MESSAGE-INVALID', '메시지 형식이 올바르지 않아요'],
])
const SEND_ERROR_FALLBACK = '메시지를 보내지 못했어요. 잠시 후 다시 시도해 주세요.'

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
  // SEND 거부(ERROR 프레임)의 입력 복원용 — clientMessageId → 보낸 본문.
  // 봉투의 clientMessageId와 일치할 때만 복원 — 상한을 두어 성공 송신 잔여를 정리한다.
  const pendingSendsRef = useRef(new Map<string, string>())

  const room = roomsQuery.data?.find((r) => r.roomId === roomId)
  // useInfiniteQuery — pages[0]이 가장 오래된 페이지라 flat()이 createdAt 오름차순
  const messages = messagesQuery.data?.pages.flat() ?? []
  const closed = room?.status === 'CLOSED'

  const me = queryClient.getQueryData<UserResponse>(queryKeys.users.me)

  // 스트림 상단 sentinel — 뷰포트에 들어오면 이전 페이지 로드.
  // prepend 직전 scrollHeight를 앵커로 잡아 로드 후 증가분만큼 뷰포트를 보정한다
  // (Safari는 overflow-anchor 미지원 — 수동 보정 필요).
  const topSentinelRef = useRef<HTMLDivElement>(null)
  const prependAnchorRef = useRef<number | null>(null)
  const hasPreviousPage = messagesQuery.hasPreviousPage
  const isFetchingPrev = messagesQuery.isFetchingPreviousPage
  const fetchPreviousPage = messagesQuery.fetchPreviousPage
  useEffect(() => {
    const el = topSentinelRef.current
    if (el === null || !hasPreviousPage) return
    const io = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && prependAnchorRef.current === null) {
        prependAnchorRef.current = document.documentElement.scrollHeight
        void fetchPreviousPage()
      }
    })
    io.observe(el)
    return () => io.disconnect()
  }, [hasPreviousPage, fetchPreviousPage])
  useEffect(() => {
    if (prependAnchorRef.current === null || isFetchingPrev) return
    const delta = document.documentElement.scrollHeight - prependAnchorRef.current
    prependAnchorRef.current = null
    if (delta > 0) window.scrollBy(0, delta)
  }, [isFetchingPrev])

  // 방 입장 시 /topic/rooms/{id} 구독 — 수신 메시지는 캐시 append + 방 목록 무효화로 반영.
  // 방 목록에 없는 방(비멤버·미존재)은 구독하지 않는다 — 서버 거부(ERROR+세션 종료)가
  // 재연결 재구독과 결합해 무한 폭풍이 되는 것을 차단. cleanup으로 구독을 해제한다.
  const roomKnown = room !== undefined
  useEffect(() => {
    if (!Number.isFinite(roomId) || !roomKnown) return
    // 단절 중 유실분은 push로 오지 않는다 — 재연결 시 최신 페이지로 리셋해 gap을 메운다
    const offConnect = onStompConnect(() => {
      void queryClient.resetQueries({ queryKey: queryKeys.chat.messages(roomId) })
    })
    const offTopic = subscribeTopic(`/topic/rooms/${roomId}`, (msg) => {
      try {
        appendRoomMessage(queryClient, roomId, JSON.parse(msg.body) as ChatMessage)
      } catch {
        void queryClient.resetQueries({ queryKey: queryKeys.chat.messages(roomId) })
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.rooms })
    })
    return () => {
      offConnect()
      offTopic()
    }
  }, [roomId, roomKnown, queryClient])

  // 서버 송신 거부(ERROR) 표면화 — 봉투 clientMessageId가 pending 송신과 일치하면 입력 복원.
  // 매칭이 안 돼도 봉투 code가 있으면 거부 사실 자체는 사용자에게 보인다 (무표시 유실 방지)
  useEffect(() => {
    return onStompError(({ code, clientMessageId }) => {
      if (code === null) return // 인증 실패 등 비JSON ERROR — 송신과 무관
      if (clientMessageId !== null) {
        const content = pendingSendsRef.current.get(clientMessageId)
        if (content !== undefined) {
          pendingSendsRef.current.delete(clientMessageId)
          setContent(content)
        }
      }
      setSendError(SEND_ERROR_MESSAGES.get(code) ?? SEND_ERROR_FALLBACK)
      // 방 상태가 뒤바뀐 거부는 목록을 갱신해 closed 표시를 즉시 반영한다
      if (
        code === 'CHAT-ROOM-CLOSED' ||
        code === 'CHAT-NOT-MEMBER' ||
        code === 'CHAT-ROOM-NOT-FOUND'
      ) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.rooms })
      }
    })
  }, [queryClient])

  // 최신 메시지까지 읽음 처리 — 보고 있는 방의 unread를 0으로 만든다.
  // 페이지는 createdAt 오름차순이라 max(id)를 써야 커서 역전이 없다
  const latestId = messages.length > 0 ? Math.max(...messages.map((m) => m.id)) : null
  const markReadMutate = markRead.mutate
  useEffect(() => {
    if (latestId === null || lastReadSentRef.current === latestId) return
    lastReadSentRef.current = latestId
    markReadMutate(latestId)
  }, [latestId, markReadMutate])

  // 최신 메시지 id가 바뀔 때만 하단으로 — 이전 페이지 prepend는 스크롤을 건드리지 않는다
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [latestId])

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = content.trim()
    if (trimmed.length === 0 || closed || send.isPending) return
    const clientMessageId = crypto.randomUUID()
    const pending = pendingSendsRef.current
    if (pending.size >= 20) pending.delete(pending.keys().next().value!)
    pending.set(clientMessageId, trimmed)
    send.mutate(
      { content: trimmed, clientMessageId },
      {
        onSuccess: () => {
          setContent('')
          setSendError(null)
        },
        onError: () => setSendError(SEND_ERROR_FALLBACK),
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
            {` · 멤버 ${room.memberCount}명`}
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

      <div ref={topSentinelRef} />
      {messagesQuery.isFetchingPreviousPage && (
        <p className="mt-4 text-center text-sm font-medium text-text-muted">
          이전 메시지를 불러오는 중…
        </p>
      )}
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
