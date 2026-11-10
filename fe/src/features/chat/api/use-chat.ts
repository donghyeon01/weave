import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../lib/react-query'
import { publish } from '../../../lib/stomp'
import type { ChatMessage, ChatSendPayload } from '../types'
import { CHAT_PAGE_LIMIT, fetchChatMessages, fetchChatRooms, postRoomRead } from './chat'

export function useChatRooms() {
  return useQuery({
    queryKey: queryKeys.chat.rooms,
    queryFn: fetchChatRooms,
  })
}

// 커서 페이지네이션 — 첫 페이지는 최신 50건, fetchPreviousPage로 더 오래된
// 페이지를 앞에 붙인다. pages[0]이 가장 오래된 페이지라 flat() 순서가 오름차순.
export function useChatMessages(roomId: number | null) {
  return useInfiniteQuery({
    queryKey: queryKeys.chat.messages(roomId ?? 0),
    queryFn: ({ pageParam }) => fetchChatMessages(roomId ?? 0, pageParam),
    initialPageParam: undefined as number | undefined,
    // 최신 방향 페이지는 없다 — 새 메시지는 STOMP 브로드캐스트 → 캐시 append로 반영한다
    getNextPageParam: () => undefined,
    // 계약 커서는 id < cursor — be가 페이지를 createdAt으로 재정렬해 응답하므로
    // 첫 요소가 아니라 페이지 내 min(id)를 써야 역전 시 중복·누락이 없다.
    // 반환 건수가 limit 미만이면 더 이상 옛날 메시지가 없다
    getPreviousPageParam: (firstPage) =>
      firstPage.length >= CHAT_PAGE_LIMIT
        ? Math.min(...firstPage.map((m) => m.id))
        : undefined,
    enabled: roomId !== null,
  })
}

/**
 * STOMP 브로드캐스트는 이력 응답과 동형 — 캐시 최신 페이지 끝에 직접 append한다.
 * messages를 invalidate하면 infinite query가 pageParams[0]→getNextPageParam 체인으로
 * 리패치해 prepend 상태에서 최신 페이지가 유실되므로(refetch 의미론) 금지.
 * gap 복구(단절 중 유실)는 호출부가 resetQueries로 처리한다.
 */
export function appendRoomMessage(
  queryClient: QueryClient,
  roomId: number,
  message: ChatMessage,
) {
  queryClient.setQueryData<InfiniteData<ChatMessage[]>>(
    queryKeys.chat.messages(roomId),
    (old) => {
      if (old === undefined || old.pages.length === 0) return old
      const last = old.pages[old.pages.length - 1]
      if (last.some((m) => m.id === message.id)) return old
      const pages = [...old.pages]
      pages[pages.length - 1] = [...last, message]
      return { ...old, pages }
    },
  )
}

// SEND /app/rooms/{id} — 서버 브로드캐스트(/topic/rooms/{id})가 캐시 append를 유도한다.
export function useSendMessage(roomId: number) {
  return useMutation({
    mutationFn: async (payload: ChatSendPayload) => {
      publish(`/app/rooms/${roomId}`, payload)
    },
  })
}

export function useMarkRead(roomId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (lastReadMessageId: number) => postRoomRead(roomId, lastReadMessageId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.rooms }),
  })
}
