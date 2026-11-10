import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { queryKeys } from '../../../lib/react-query'
import { publish } from '../../../lib/stomp'
import type { ChatSendPayload } from '../types'
import { fetchChatMessages, fetchChatRooms, postRoomRead } from './chat'

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
    // 최신 방향 페이지는 없다 — 새 메시지는 STOMP 브로드캐스트 → 무효화로 반영한다
    getNextPageParam: () => undefined,
    // 반환 건수가 limit(50) 미만이면 더 이상 옛날 메시지가 없다
    getPreviousPageParam: (firstPage) =>
      firstPage.length >= 50 ? firstPage[0]?.id : undefined,
    enabled: roomId !== null,
  })
}

// SEND /app/rooms/{id} — 서버 브로드캐스트(/topic/rooms/{id})가 목록 무효화를 유도한다.
// 즉시 반영을 위해 성공 시 messages도 무효화한다.
export function useSendMessage(roomId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (payload: ChatSendPayload) => {
      publish(`/app/rooms/${roomId}`, payload)
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.messages(roomId) }),
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
